package com.ahogek.cttserver.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration.
 *
 * <p>Provides security-related beans such as PasswordEncoder.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Configuration
public class SecurityConfig {

    /**
     * Provides BCrypt password encoder.
     *
     * @return the password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
