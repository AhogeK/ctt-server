package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository.SessionRow;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
import com.ahogek.cttserver.stats.achievement.repository.AchievementProgressRepository;
import com.ahogek.cttserver.stats.achievement.repository.UserAchievementRepository;
import com.ahogek.cttserver.stats.materialization.entity.DailyStats;
import com.ahogek.cttserver.stats.materialization.repository.DailyStatsRepository;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes an account's data as a file the desktop client can import.
 *
 * <p>The shape is not ours to choose: the plugin reads it with Gson into a fixed model, refuses any
 * {@code exportVersion} other than {@code 1.0}, and parses every timestamp with {@link
 * DateTimeFormatter#ISO_LOCAL_DATE_TIME} — a string carrying {@code Z} makes the whole import fail
 * rather than skip one row. The document therefore keeps the plugin's exact top-level structure and
 * field names, and converts every instant into the caller's local time using the offset the request
 * carries. Everything extra rides in {@code serverExport}: Gson ignores unknown fields, so a richer
 * document stays importable.
 *
 * <p>Two properties of the plugin model drive details here:
 *
 * <ul>
 *   <li>Its session fields are non-null, so a value that cannot be supplied (a session whose device
 *       was never registered) is written as an empty string, never as {@code null}.
 *   <li>Import inserts with {@code is_deleted = 0} and de-duplicates by {@code sessionUuid}, so
 *       soft-deleted sessions are left out — exporting them would resurrect rows the user deleted.
 * </ul>
 *
 * <p>Sessions stream page by page: a long-running account can hold hundreds of thousands of rows,
 * and the response is written as they are read rather than assembled whole.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@Service
public class UserExportService {

    /** Export format version, matched against the plugin importer's accepted value. */
    private static final String EXPORT_VERSION = "1.0";

    /** Sessions read per page while streaming. */
    private static final int SESSION_PAGE_SIZE = 1000;

    /** Timestamp format the plugin parses: local date-time, no zone, optional fraction. */
    private static final DateTimeFormatter LOCAL_TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Largest offset the endpoint accepts, in minutes (the plugin's own range). */
    public static final int MAX_TIMEZONE_OFFSET_MINUTES = 720;

    private final UserRepository userRepository;
    private final CodingSessionRepository codingSessionRepository;
    private final DeviceRepository deviceRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementProgressRepository achievementProgressRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public UserExportService(
            UserRepository userRepository,
            CodingSessionRepository codingSessionRepository,
            DeviceRepository deviceRepository,
            DailyStatsRepository dailyStatsRepository,
            UserAchievementRepository userAchievementRepository,
            AchievementProgressRepository achievementProgressRepository,
            UserOAuthAccountRepository oauthAccountRepository,
            ApiKeyRepository apiKeyRepository,
            RefreshTokenRepository refreshTokenRepository,
            ObjectMapper objectMapper,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.codingSessionRepository = codingSessionRepository;
        this.deviceRepository = deviceRepository;
        this.dailyStatsRepository = dailyStatsRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.achievementProgressRepository = achievementProgressRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    /**
     * Streams the account's export document into the response.
     *
     * @param userId the account to export
     * @param timezoneOffsetMinutes the caller's offset from UTC in minutes; every timestamp is
     *     written in this zone because the importer reads them as local times
     * @param out the response stream
     * @throws IOException when the client disconnects or the stream fails; the response is already
     *     committed at that point and can only be cut short
     */
    @Transactional(readOnly = true)
    public void writeExport(UUID userId, int timezoneOffsetMinutes, OutputStream out)
            throws IOException {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(timezoneOffsetMinutes * 60);
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user disappeared mid-request"));

        Map<UUID, Device> devices = devicesOf(userId);
        long sessionCount = codingSessionRepository.countByUserIdAndIsDeletedFalse(userId);

        auditLogService.log(
                userId,
                AuditAction.DATA_EXPORTED,
                ResourceType.USER,
                userId.toString(),
                SecuritySeverity.INFO,
                AuditDetails.extension(
                        Map.of(
                                "sessions", sessionCount,
                                "timezoneOffsetMinutes", timezoneOffsetMinutes)));

        JsonGenerator generator = objectMapper.getFactory().createGenerator(out);
        generator.writeStartObject();
        generator.writeStringField("exportVersion", EXPORT_VERSION);
        generator.writeStringField("exportTime", LocalDateTime.now(offset).format(LOCAL_TIMESTAMP));
        generator.writeNumberField("totalSessions", sessionCount);
        writeSessions(generator, userId, offset, devices);
        writeServerExport(generator, user, offset, devices);
        generator.writeEndObject();
        generator.flush();
    }

    private void writeSessions(
            JsonGenerator generator, UUID userId, ZoneOffset offset, Map<UUID, Device> devices)
            throws IOException {
        generator.writeArrayFieldStart("sessions");
        int page = 0;
        List<CodingSession> batch;
        do {
            batch =
                    codingSessionRepository
                            .findByUserIdAndIsDeletedFalse(
                                    userId,
                                    PageRequest.of(
                                            page,
                                            SESSION_PAGE_SIZE,
                                            Sort.by(Sort.Direction.ASC, "id")))
                            .getContent();
            for (CodingSession session : batch) {
                writeSession(generator, session, offset, devices);
            }
            page++;
        } while (batch.size() == SESSION_PAGE_SIZE);
        generator.writeEndArray();
    }

    private void writeSession(
            JsonGenerator generator,
            CodingSession session,
            ZoneOffset offset,
            Map<UUID, Device> devices)
            throws IOException {
        Device device = devices.get(session.getOriginDeviceId());

        generator.writeStartObject();
        generator.writeStringField("sessionUuid", session.getSessionUuid().toString());
        generator.writeStringField("userId", session.getUserId().toString());
        generator.writeStringField("projectName", session.getProjectName());
        generator.writeStringField("language", session.getLanguage());
        generator.writeStringField("platform", text(device == null ? null : device.getPlatform()));
        generator.writeStringField("ideName", text(device == null ? null : device.getIdeName()));
        generator.writeStringField("startTime", local(session.getStartTime(), offset));
        generator.writeStringField("endTime", local(session.getEndTime(), offset));
        generator.writeStringField("lastModified", local(session.getClientModifiedAt(), offset));
        generator.writeEndObject();
    }

    private void writeServerExport(
            JsonGenerator generator, User user, ZoneOffset offset, Map<UUID, Device> devices)
            throws IOException {
        generator.writeObjectFieldStart("serverExport");
        generator.writeStringField("exportedBy", "ctt-server");
        generator.writeNumberField("timezoneOffsetMinutes", offset.getTotalSeconds() / 60);
        generator.writeStringField("generatedAt", Instant.now().toString());

        writeAccount(generator, user);
        writeDevices(generator, devices);
        writeDailyStats(generator, user.getId());
        writeAchievements(generator, user.getId());
        writeOauthAccounts(generator, user.getId());
        writeLoginSessions(generator, user.getId());
        writeApiKeys(generator, user.getId());

        generator.writeEndObject();
    }

    private void writeAccount(JsonGenerator generator, User user) throws IOException {
        generator.writeObjectFieldStart("account");
        generator.writeStringField("id", user.getId().toString());
        generator.writeStringField("email", user.getEmail());
        generator.writeStringField("displayName", text(user.getDisplayName()));
        generator.writeBooleanField("emailVerified", user.getEmailVerifiedAt() != null);
        generator.writeStringField("createdAt", user.getCreatedAt().toString());
        generator.writeStringField("lastLoginAt", nullable(user.getLastLoginAt()));
        generator.writeStringField("termsVersion", text(user.getTermsVersion()));
        generator.writeEndObject();
    }

    private void writeDevices(JsonGenerator generator, Map<UUID, Device> devices)
            throws IOException {
        generator.writeArrayFieldStart("devices");
        for (Device device : devices.values()) {
            generator.writeStartObject();
            generator.writeStringField("id", device.getId().toString());
            generator.writeStringField("deviceName", text(device.getDeviceName()));
            generator.writeStringField("platform", text(device.getPlatform()));
            generator.writeStringField("ideName", text(device.getIdeName()));
            generator.writeStringField("createdAt", device.getCreatedAt().toString());
            generator.writeStringField("lastSeenAt", nullable(device.getLastSeenAt()));
            generator.writeStringField("revokedAt", nullable(device.getRevokedAt()));
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeDailyStats(JsonGenerator generator, UUID userId) throws IOException {
        generator.writeArrayFieldStart("dailyStats");
        for (DailyStats stat : dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId)) {
            generator.writeStartObject();
            generator.writeStringField("date", stat.getUtcDate().toString());
            generator.writeNumberField("mergedSeconds", stat.getMergedSeconds());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeAchievements(JsonGenerator generator, UUID userId) throws IOException {
        generator.writeArrayFieldStart("achievements");
        for (UserAchievement achievement : userAchievementRepository.findByUserId(userId)) {
            generator.writeStartObject();
            generator.writeStringField("code", achievement.getAchievementCode());
            generator.writeStringField("periodKey", text(achievement.getPeriodKey()));
            generator.writeStringField("unlockedAt", achievement.getUnlockedAt().toString());
            generator.writeEndObject();
        }
        generator.writeEndArray();

        generator.writeArrayFieldStart("achievementProgress");
        for (AchievementProgress progress : achievementProgressRepository.findByUserId(userId)) {
            generator.writeStartObject();
            generator.writeStringField("achievementType", progress.getAchievementType().name());
            generator.writeNumberField("progress", progress.getProgress());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeOauthAccounts(JsonGenerator generator, UUID userId) throws IOException {
        generator.writeArrayFieldStart("oauthAccounts");
        for (UserOAuthAccount account : oauthAccountRepository.findAllByUserId(userId)) {
            generator.writeStartObject();
            generator.writeStringField("provider", account.getProvider().name());
            generator.writeStringField("providerLogin", text(account.getProviderLogin()));
            generator.writeStringField("providerEmail", text(account.getProviderEmail()));
            generator.writeStringField("createdAt", account.getCreatedAt().toString());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeLoginSessions(JsonGenerator generator, UUID userId) throws IOException {
        generator.writeArrayFieldStart("loginSessions");
        for (SessionRow session :
                refreshTokenRepository.findActiveSessionRows(userId, Instant.now())) {
            generator.writeStartObject();
            generator.writeStringField("id", session.getSessionId().toString());
            generator.writeStringField("kind", session.getIssuedFor());
            generator.writeStringField("deviceId", text(session.getDeviceId()));
            generator.writeStringField("userAgent", text(session.getUserAgent()));
            generator.writeStringField("createdAt", session.getCreatedAt().toString());
            generator.writeStringField("lastUsedAt", nullable(session.getLastUsedAt()));
            generator.writeStringField("expiresAt", session.getExpiresAt().toString());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeApiKeys(JsonGenerator generator, UUID userId) throws IOException {
        generator.writeArrayFieldStart("apiKeys");
        for (ApiKey key : apiKeyRepository.findAllByUserId(userId)) {
            generator.writeStartObject();
            generator.writeStringField("name", text(key.getName()));
            generator.writeStringField("scopes", text(key.getScopes()));
            generator.writeStringField("createdAt", key.getCreatedAt().toString());
            generator.writeStringField("lastUsedAt", nullable(key.getLastUsedAt()));
            generator.writeStringField("revokedAt", nullable(key.getRevokedAt()));
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private Map<UUID, Device> devicesOf(UUID userId) {
        Map<UUID, Device> devices = new HashMap<>();
        for (Device device : deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId)) {
            devices.put(device.getId(), device);
        }
        return devices;
    }

    private String local(Instant instant, ZoneOffset offset) {
        return OffsetDateTime.ofInstant(instant, offset).toLocalDateTime().format(LOCAL_TIMESTAMP);
    }

    private String nullable(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /** Never null: the plugin model rejects null strings on import. */
    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
