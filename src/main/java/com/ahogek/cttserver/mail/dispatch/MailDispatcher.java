package com.ahogek.cttserver.mail.dispatch;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.mail.entity.MailOutbox;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Low-level mail dispatcher for SMTP delivery.
 *
 * <p>Responsible for converting {@link MailOutbox} entities into RFC-compliant {@link MimeMessage}
 * and executing physical SMTP I/O via {@link JavaMailSender}.
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>SRP</b>: Only handles SMTP dispatch, no database state transitions
 *   <li><b>Exception transparency</b>: Propagates {@link MessagingException} to caller for
 *       retry/circuit-breaker handling
 *   <li><b>Multipart Alternative</b>: Sends both HTML and plain-text for anti-spam compliance
 * </ul>
 *
 * @author AhogeK
 * @since 2026-03-20
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender mailSender;
    private final CttMailProperties mailProperties;

    public MailDispatcher(JavaMailSender mailSender, CttMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    /**
     * Executes physical SMTP delivery for the given outbox entry.
     *
     * <p>Constructs a multipart/alternative message with both HTML and plain-text bodies, ensuring
     * compatibility with clients that prefer text-only content (e.g., Apple Watch, terminal
     * readers) and reducing spam filtering risk.
     *
     * @param outbox the pre-rendered mail outbox entity
     * @throws MessagingException if SMTP protocol error or message construction fails
     * @throws UnsupportedEncodingException if sender name encoding fails
     */
    public void dispatch(MailOutbox outbox)
            throws MessagingException, UnsupportedEncodingException {
        log.debug(
                "Dispatching email [id={}, traceId={}] to {}",
                outbox.getId(),
                outbox.getTraceId(),
                outbox.getRecipient());

        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper =
                new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(mailProperties.from().address(), mailProperties.from().name());
        helper.setTo(outbox.getRecipient());
        helper.setSubject(outbox.getSubject());
        helper.setText(outbox.getBodyText(), outbox.getBodyHtml());

        mailSender.send(message);

        log.info("Dispatched email [id={}] to {}", outbox.getId(), outbox.getRecipient());
    }
}
