package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.mail.entity.MailOutbox;

import java.util.Map;

final class MailOutboxAuditDetails {

    private MailOutboxAuditDetails() {}

    static AuditDetails of(MailOutbox outbox) {
        return AuditDetails.extension(
                Map.of(
                        "mailOutboxId", outbox.getId().toString(),
                        "templateName", outbox.getTemplateCode(),
                        "retryCount", outbox.getRetryCount()));
    }
}
