package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Audit retention policy.
 *
 * <p>A period of zero keeps every row: the retention period is an operations/compliance decision,
 * not an application default. Set it only after that decision is recorded.
 *
 * @param days how long to keep security audit rows; zero disables deletion
 * @param batchSize maximum rows deleted per statement
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-09
 */
@Validated
@ConfigurationProperties(prefix = "ctt.audit.retention")
public record AuditRetentionProperties(
        @DefaultValue("0") @Min(0) int days, @DefaultValue("1000") @Min(1) int batchSize) {}
