package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only service for retrieving user profile information.
 *
 * <p>Provides a thin projection of the {@link User} entity to the API layer, intentionally
 * excluding sensitive fields (passwordHash, lastLoginIp, version, emailVerifiedAt raw value).
 *
 * <p>This service is read-only — all methods execute within a read-only transaction to ensure the
 * Session invariant (no dirty-checking overhead, no accidental flush).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-01
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    public UserProfileService(
            UserRepository userRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    }

    /**
     * Retrieves the profile for the given user.
     *
     * @param userId the user's unique identifier
     * @return the user profile DTO
     * @throws NotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));
        boolean emailChangePending =
                emailVerificationTokenRepository.existsPendingChangeEmailTokenByUserId(
                        userId, Instant.now());
        return UserProfileResponse.fromEntity(user, emailChangePending);
    }
}
