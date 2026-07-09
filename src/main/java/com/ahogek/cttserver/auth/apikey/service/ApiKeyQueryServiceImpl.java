package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link ApiKeyQueryService}.
 *
 * <p>Read-only lookups with BOLA protection. Results are ordered by creation time (most recent
 * first) for consistent UI rendering.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Service
public class ApiKeyQueryServiceImpl implements ApiKeyQueryService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyQueryServiceImpl(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID userId) {
        return apiKeyRepository.findAllByUserId(userId).stream()
                .sorted(
                        Comparator.comparing(
                                        com.ahogek.cttserver.auth.apikey.entity.ApiKey
                                                ::getCreatedAt)
                                .reversed())
                .map(ApiKeyResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKey(UUID userId, UUID id) {
        return apiKeyRepository
                .findByIdAndUserId(id, userId)
                .map(ApiKeyResponse::fromEntity)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AUTH_010));
    }
}
