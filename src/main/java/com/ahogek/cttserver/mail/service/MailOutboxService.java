package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.mail.template.EmailVerificationTemplateData;
import com.ahogek.cttserver.mail.template.MailTemplateRenderer;
import com.ahogek.cttserver.mail.template.PasswordResetTemplateData;

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

    private static final String BIZ_TYPE_VERIFICATION = "REGISTER_VERIFY";
    private static final String BIZ_TYPE_PASSWORD_RESET = "RESET_PASSWORD";

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private static final Duration VERIFICATION_RATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration PASSWORD_RESET_RATE_WINDOW = Duration.ofMinutes(5);
    private static final int RATE_LIMIT_THRESHOLD = 3;

    private static final List<MailOutboxStatus> RATE_LIMIT_STATUSES =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT);

    private final MailOutboxRepository repository;
    private final MailTemplateRenderer renderer;
    private final CttMailProperties properties;

    public MailOutboxService(
            MailOutboxRepository repository,
            MailTemplateRenderer renderer,
            CttMailProperties properties) {
        this.repository = repository;
        this.renderer = renderer;
        this.properties = properties;
    }

    @Transactional
    public void enqueueVerificationEmail(UUID userId, String username, String email, String token) {
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

    @Transactional
    public void enqueuePasswordResetEmail(
            UUID userId, String username, String email, String token) {
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
        long count = repository.countDuplicates(email, bizType, RATE_LIMIT_STATUSES, windowStart);

        if (count >= RATE_LIMIT_THRESHOLD) {
            throw new TooManyRequestsException(ErrorCode.MAIL_004);
        }
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
        outbox.setMaxRetries(properties.retry().maxAttempts());
        return outbox;
    }
}
