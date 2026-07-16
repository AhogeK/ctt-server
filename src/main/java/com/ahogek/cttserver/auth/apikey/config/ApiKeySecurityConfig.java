package com.ahogek.cttserver.auth.apikey.config;

import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.client.ApiKeyAuthenticationFilter;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyService;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.ratelimit.core.RedisRateLimiter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration for API Key authentication components.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-10
 */
@Configuration
public class ApiKeySecurityConfig {

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            AuditLogService auditLogService,
            SecurityProperties securityProperties,
            ObjectMapper objectMapper,
            RedisRateLimiter redisRateLimiter) {
        return new ApiKeyAuthenticationFilter(
                apiKeyService, auditLogService, securityProperties, objectMapper, redisRateLimiter);
    }
}
