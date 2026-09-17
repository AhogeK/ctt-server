package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deletes an account and everything that belongs to it.
 *
 * <p>The user row is removed outright, and the schema does the rest: every table that hangs off a
 * user references it with {@code ON DELETE CASCADE}, so sessions, statistics, achievements, sync
 * state, devices, API keys, refresh tokens and OAuth links all go with it. That is why deletion is
 * expressed as a delete rather than as a status change — a list of tables to clean up by hand would
 * silently miss the next table added, while the cascade cannot.
 *
 * <p>What survives is the audit trail: {@code audit_logs} references the user with {@code ON DELETE
 * SET NULL}, so security events stay readable with the account reference cleared. That is the
 * schema's own answer to "deletion without losing the record of what happened", and it is why this
 * service does not need to anonymize anything.
 *
 * <p>The user's own history is not lost to them: the plugin is the source of truth for it and re-
 * pushes after a new registration, so the server copy is a convenience rather than the only one.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-17
 */
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AccountDeletionService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    /**
     * Deletes the account and its data.
     *
     * <p>The caller is expected to have established that the session belongs to the account owner;
     * what this adds is proof that the person at the keyboard knows the password, which a stolen
     * session token alone does not establish. Accounts created through an OAuth provider have no
     * password to prove, and for them the session is the whole of the available evidence.
     *
     * <p>Access tokens are not revoked, and cannot be: they are stateless snapshots, so the status
     * they carry is the one that was true when they were issued. A deleted account's token
     * therefore keeps working until it expires, within the access-token TTL — the same window
     * logout leaves open. What makes the deletion final instead is that every credential which
     * could be used again is gone with the row, and that no new one can be obtained: login,
     * refresh, password reset, OAuth and API-key authentication all read from the database and find
     * nothing.
     *
     * @param userId the account to delete
     * @param password the account password, or {@code null} for accounts that have none
     * @throws NotFoundException when the account no longer exists
     * @throws ForbiddenException when a password is set and none was supplied
     * @throws UnauthorizedException when a password is set and the supplied one does not match
     */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));

        String passwordHash = user.getPasswordHash();
        if (passwordHash != null) {
            if (password == null || password.isBlank()) {
                throw new ForbiddenException(ErrorCode.USER_013);
            }
            if (!passwordEncoder.matches(password, passwordHash)) {
                throw new UnauthorizedException(ErrorCode.USER_014);
            }
        }

        userRepository.delete(user);

        // The listener runs after commit, by which point this row no longer exists — so the event
        // carries the account id as its resource and no user reference, which is also the state the
        // foreign key would reduce it to.
        auditLogService.logSuccess(
                null, AuditAction.ACCOUNT_DELETED, ResourceType.USER, userId.toString());
    }
}
