package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Sync engine tuning parameters.
 *
 * <p><strong>Configuration Prefix:</strong> {@code ctt.sync}
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-02
 */
@Validated
@ConfigurationProperties(prefix = "ctt.sync")
public record SyncProperties(@Min(1) @Max(10_000) @DefaultValue("1000") int pullBatchSize) {}
