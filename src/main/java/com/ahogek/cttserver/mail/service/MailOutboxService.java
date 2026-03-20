package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.common.context.MdcKey;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.mail.template.EmailVerificationTemplateData;
import com.ahogek.cttserver.mail.template.MailTemplateRenderer;
import com.ahogek.cttserver.mail.template.PasswordResetTemplateData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mail outbox write-side service.
 *
 * <p>Handles email enqueue operations with rate limiting and template pre-rendering. Does not
 * perform actual SMTP delivery — that's handled by the scheduler.
 *
 * @author AhogeK
 * @since 2026-03-20
 */
@Service
public class MailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxService.class);

    private static final String BIZ_TYPE_VERIFICATION = "REGISTER_VERIFY";
    private static final String BIZ_TYPE_PASSWORD_RESET = "RESET_PASSWORD";

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private static final Duration VERIFICATION_RATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration PASSWORD_RESET_RATE_WINDOW = Duration.ofMinutes(5);
    private static final int RATE_LIMIT_THRESHOLD = 3;

    private static final Duration IDEMPOTENT_WINDOW = Duration.ofMinutes(10);

    private static final List<MailOutboxStatus> ACTIVE_STATUSES =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT);

    private final MailOutboxRepository repository;
    private final MailTemplateRenderer renderer;
    private final CttMailProperties properties;
    private final AuditLogService auditLog;

    public MailOutboxService(
            MailOutboxRepository repository,
            MailTemplateRenderer renderer,
            CttMailProperties properties,
            AuditLogService auditLog) {
        this.repository = repository;
        this.renderer = renderer;
        this.properties = properties;
        this.auditLog = auditLog;
    }

    /**
     * Enqueues a verification email for delivery.
     *
     * <p>Rate-limited to 3 requests per 1 minute window. Pre-renders HTML and text templates before
     * persistence.
     *
     * @param userId the user's unique identifier
     * @param username the user's display name for email personalization
     * @param email the recipient email address
     * @param token the verification token for the email link
     * @throws TooManyRequestsException if rate limit is exceeded (ErrorCode.MAIL_004)
     */
    @Transactional
    public void enqueueVerificationEmail(UUID userId, String username, String email, String token) {
        if (isIdempotentSkip(userId, email, BIZ_TYPE_VERIFICATION)) {
            return;
        }

        checkRateLimit(email, BIZ_TYPE_VERIFICATION, VERIFICATION_RATE_WINDOW);

        String link = buildVerificationLink(token);
        var data = new EmailVerificationTemplateData(username, link, VERIFICATION_TOKEN_TTL);

        MailOutbox outbox =
                buildOutboxEntity(
                        userId,
                        email,
                        BIZ_TYPE_VERIFICATION,
                        "Verify Your Email Address",
                        data.getTemplateName(),
                        renderer.renderHtml(data),
                        renderer.renderText(data),
                        data.getVariables());

        repository.save(outbox);
    }

    /**
     * Enqueues a password reset email for delivery.
     *
     * <p>Rate-limited to 3 requests per 5 minutes window. Pre-renders HTML and text templates
     * before persistence.
     *
     * @param userId the user's unique identifier
     * @param username the user's display name for email personalization
     * @param email the recipient email address
     * @param token the password reset token for the email link
     * @throws TooManyRequestsException if rate limit is exceeded (ErrorCode.MAIL_004)
     */
    @Transactional
    public void enqueuePasswordResetEmail(
            UUID userId, String username, String email, String token) {
        if (isIdempotentSkip(userId, email, BIZ_TYPE_PASSWORD_RESET)) {
            return;
        }

        checkRateLimit(email, BIZ_TYPE_PASSWORD_RESET, PASSWORD_RESET_RATE_WINDOW);

        String link = buildPasswordResetLink(token);
        var data = new PasswordResetTemplateData(username, link, PASSWORD_RESET_TOKEN_TTL);

        MailOutbox outbox =
                buildOutboxEntity(
                        userId,
                        email,
                        BIZ_TYPE_PASSWORD_RESET,
                        "Reset Your Password",
                        data.getTemplateName(),
                        renderer.renderHtml(data),
                        renderer.renderText(data),
                        data.getVariables());

        repository.save(outbox);
    }

    private void checkRateLimit(String email, String bizType, Duration window) {
        Instant windowStart = Instant.now().minus(window);
        long count = repository.countDuplicates(email, bizType, ACTIVE_STATUSES, windowStart);

        if (count >= RATE_LIMIT_THRESHOLD) {
            throw new TooManyRequestsException(ErrorCode.MAIL_004);
        }
    }

    private boolean isIdempotentSkip(UUID bizId, String recipient, String bizType) {
        Instant windowStart = Instant.now().minus(IDEMPOTENT_WINDOW);
        boolean exists =
                repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                        recipient, bizType, bizId, ACTIVE_STATUSES, windowStart);

        if (exists) {
            log.info(
                    "Idempotent skip: email '{}' of type '{}' for user '{}' already exists in window",
                    recipient,
                    bizType,
                    bizId);

            auditLog.log(
                    bizId,
                    AuditAction.MAIL_IDEMPOTENT_SKIP,
                    ResourceType.MAIL_OUTBOX,
                    recipient,
                    SecuritySeverity.INFO,
                    AuditDetails.extension(
                            Map.of(
                                    "bizType",
                                    bizType,
                                    "recipient",
                                    recipient,
                                    "windowMinutes",
                                    IDEMPOTENT_WINDOW.toMinutes())));

            return true;
        }

        return false;
    }

    private String buildVerificationLink(String token) {
        return properties.frontend().baseUrl() + "/verify-email?token=" + token;
    }

    private String buildPasswordResetLink(String token) {
        return properties.frontend().baseUrl() + "/reset-password?token=" + token;
    }

    private MailOutbox buildOutboxEntity(
            UUID bizId,
            String recipient,
            String bizType,
            String subject,
            String templateCode,
            String bodyHtml,
            String bodyText,
            Map<String, Object> payload) {
        MailOutbox outbox = new MailOutbox();
        outbox.setBizId(bizId);
        outbox.setRecipient(recipient);
        outbox.setBizType(bizType);
        outbox.setSubject(subject);
        outbox.setTemplateCode(templateCode);
        outbox.setBodyHtml(bodyHtml);
        outbox.setBodyText(bodyText);
        outbox.setPayload(payload);
        outbox.setTraceId(extractCurrentTraceId());
        outbox.setMaxRetries(properties.retry().maxAttempts());
        outbox.setStatus(MailOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(Instant.now());
        return outbox;
    }

    private String extractCurrentTraceId() {
        return RequestContext.current()
                .map(RequestInfo::traceId)
                .orElseGet(this::getTraceIdFromMdc);
    }

    private String getTraceIdFromMdc() {
        String traceId = MDC.get(MdcKey.TRACE_ID);
        return traceId != null ? traceId : "no-trace";
    }
}
