package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.crypto.ApiKeyHasher;
import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyRequest;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Default implementation of {@link ApiKeyService}.
 *
 * <p>Issues new API keys (returning the raw secret exactly once) and revokes existing keys. All
 * operations enforce BOLA protection by verifying the key belongs to the requesting user.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceImpl.class);

    static final int MAX_KEYS_PER_USER = 20;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final AuditLogService auditLogService;

    public ApiKeyServiceImpl(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ApiKeyHasher apiKeyHasher,
            AuditLogService auditLogService) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public CreateApiKeyResponse createApiKey(UUID userId, CreateApiKeyRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));

        long activeCount = apiKeyRepository.countByUserIdAndRevokedAtIsNull(userId);
        if (activeCount >= MAX_KEYS_PER_USER) {
            throw new ConflictException(ErrorCode.AUTH_014);
        }

        String rawKey = apiKeyHasher.generateRawKey();
        String keyHash = apiKeyHasher.hashKey(rawKey);
        String keyPrefix = extractPrefix(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setKeyHash(keyHash);
        apiKey.setName(request.name());
        apiKey.setScopes(request.scopes());
        if (request.expiresAt() != null) {
            apiKey.setExpiresAt(request.expiresAt().toInstant());
        }

        ApiKey saved = apiKeyRepository.save(apiKey);

        auditLogService.logSuccess(
                userId,
                AuditAction.API_KEY_CREATED,
                ResourceType.API_KEY,
                saved.getId().toString());

        log.debug("Created API key {} for user {}", saved.getId(), userId);

        return new CreateApiKeyResponse(rawKey, ApiKeyResponse.fromEntity(saved));
    }

    @Override
    @Transactional
    public void revokeApiKey(UUID userId, UUID id) {
        ApiKey apiKey =
                apiKeyRepository
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.AUTH_010));

        if (apiKey.getRevokedAt() != null) {
            return;
        }

        apiKey.revoke(Instant.now());
        apiKeyRepository.save(apiKey);

        auditLogService.logSuccess(
                userId, AuditAction.API_KEY_REVOKED, ResourceType.API_KEY, id.toString());

        log.debug("Revoked API key {} for user {}", id, userId);
    }

    private static String extractPrefix(String rawKey) {
        int markerEnd = rawKey.indexOf('_', ApiKeyHasher.KEY_PREFIX_MARKER.length());
        return rawKey.substring(ApiKeyHasher.KEY_PREFIX_MARKER.length(), markerEnd);
    }
}
