package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.mail.service.MailOutboxService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sends the non-blocking security notification for a newly registered passkey.
 *
 * <p>A mail failure must never roll back the credential: the user can still manage it, and the
 * outbox already has retry semantics for delivery failures.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class PasskeyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyNotificationService.class);

    private final MailOutboxService mailOutbox;

    public PasskeyNotificationService(MailOutboxService mailOutbox) {
        this.mailOutbox = mailOutbox;
    }

    /**
     * Enqueues the "new passkey" notification.
     *
     * @param userId the account
     * @param username the account display name
     * @param email the recipient
     * @param passkeyName the credential label
     */
    public void credentialAdded(UUID userId, String username, String email, String passkeyName) {
        try {
            mailOutbox.enqueuePasskeyAddedEmail(userId, username, email, passkeyName);
        } catch (RuntimeException e) {
            log.warn(
                    "Unable to enqueue passkey notification for user {}: {}",
                    userId,
                    e.getMessage());
        }
    }
}
