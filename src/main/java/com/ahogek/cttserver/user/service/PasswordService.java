package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Password service for managing user password operations.
 *
 * <p>Handles password-setting workflows for OAuth users who want to add a password-based login
 * method to their account.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-04
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public PasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    /**
     * Sets a password for an OAuth user who does not yet have one.
     *
     * <p>This allows OAuth-only users to add password-based authentication to their account.
     *
     * @param userId the user's unique identifier
     * @param newPassword the new password to set
     * @throws NotFoundException if no user exists with the given ID
     * @throws ConflictException if the user already has a password set
     */
    @Transactional
    public void setPassword(UUID userId, String newPassword) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));

        if (user.getPasswordHash() != null) {
            throw new ConflictException(ErrorCode.USER_015);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditLogService.logSuccess(
                userId, AuditAction.PASSWORD_SET, ResourceType.USER, userId.toString());

        log.info("Password set for user {}", userId);
    }

    /**
     * Changes the password for an authenticated user.
     *
     * <p>Verifies the current password before updating and rejects the new password if it is
     * identical to the current one.
     *
     * @param userId the user's unique identifier
     * @param currentPassword the user's current password
     * @param newPassword the new password to set
     * @throws NotFoundException if no user exists with the given ID
     * @throws ConflictException if the user has no password set or the new password equals the
     *     current one
     * @throws UnauthorizedException if the current password does not match
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));

        if (user.getPasswordHash() == null) {
            throw new ConflictException(ErrorCode.USER_015);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.USER_014);
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ConflictException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditLogService.logSuccess(
                userId, AuditAction.PASSWORD_CHANGED, ResourceType.USER, userId.toString());

        log.info("Password changed for user {}", userId);
    }
}
