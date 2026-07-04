package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-Origin Resource Sharing (CORS) configuration.
 *
 * <p>Exposes a {@link CorsConfigurationSource} bean that Spring Security consumes to evaluate CORS
 * preflight and actual requests against the policy defined under {@code ctt.security.cors}. The
 * configuration is registered for all {@code /api/**} endpoints.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-04
 */
@Configuration
public class CorsConfig {

    private final SecurityProperties.Cors cors;

    public CorsConfig(SecurityProperties securityProperties) {
        this.cors = securityProperties.cors();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedOriginPatterns(cors.allowedOriginPatterns());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setExposedHeaders(cors.exposedHeaders());
        config.setAllowCredentials(cors.allowCredentials());
        config.setMaxAge(cors.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
