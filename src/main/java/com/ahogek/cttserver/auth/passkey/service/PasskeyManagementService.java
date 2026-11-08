package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyResponse;
import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;
import com.ahogek.cttserver.auth.passkey.repository.UserPasskeyRepository;
import com.ahogek.cttserver.auth.service.RecentAuthenticationService;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.entity.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * List, rename and remove registered passkeys.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class PasskeyManagementService {

    private final UserPasskeyRepository passkeys;
    private final UserOAuthAccountRepository oauthAccounts;
    private final RecentAuthenticationService recentAuthentication;
    private final AuditLogService auditLog;

    public PasskeyManagementService(
            UserPasskeyRepository passkeys,
            UserOAuthAccountRepository oauthAccounts,
            RecentAuthenticationService recentAuthentication,
            AuditLogService auditLog) {
        this.passkeys = passkeys;
        this.oauthAccounts = oauthAccounts;
        this.recentAuthentication = recentAuthentication;
        this.auditLog = auditLog;
    }

    /**
     * Lists the account's credentials.
     *
     * @param userId the authenticated account
     * @return safe credential metadata
     */
    public List<PasskeyResponse> list(UUID userId) {
        return passkeys.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(PasskeyResponse::fromEntity)
                .toList();
    }

    /**
     * Renames a credential. Renaming is not a credential-changing operation, so it does not require
     * recent authentication.
     *
     * @param userId the owner
     * @param passkeyId the credential row id
     * @param name the new label
     * @return updated metadata
     */
    @Transactional
    public PasskeyResponse rename(UUID userId, UUID passkeyId, String name) {
        String normalized = PasskeyName.forRename(name);
        UserPasskey passkey = requireOwned(userId, passkeyId);
        passkey.rename(normalized);
        passkeys.save(passkey);
        auditLog.log(
                userId,
                AuditAction.PASSKEY_RENAMED,
                ResourceType.USER,
                userId.toString(),
                SecuritySeverity.INFO,
                AuditDetails.empty());
        return PasskeyResponse.fromEntity(passkey);
    }

    /**
     * Deletes a credential. The deletion requires recent authentication and must leave at least one
     * other login method (password or OAuth) on the account.
     *
     * @param userId the owner
     * @param passkeyId the credential row id
     * @param sessionId the current login session
     */
    @Transactional
    public void delete(UUID userId, UUID passkeyId, UUID sessionId) {
        recentAuthentication.requireRecent(userId, sessionId);
        passkeys.lockAllByUserId(userId);
        UserPasskey passkey = requireOwned(userId, passkeyId);
        User user = passkey.getUser();
        boolean lastPasskey = passkeys.countByUserId(userId) == 1;
        boolean hasPassword = user.getPasswordHash() != null;
        boolean hasOauth = oauthAccounts.countByUserId(userId) > 0;
        if (lastPasskey && !hasPassword && !hasOauth) {
            throw new ConflictException(ErrorCode.AUTH_018);
        }

        passkeys.delete(passkey);
        auditLog.log(
                userId,
                AuditAction.PASSKEY_DELETED,
                ResourceType.USER,
                userId.toString(),
                SecuritySeverity.INFO,
                AuditDetails.empty());
    }

    private UserPasskey requireOwned(UUID userId, UUID passkeyId) {
        return passkeys.findByIdAndUserId(passkeyId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AUTH_035));
    }
}
