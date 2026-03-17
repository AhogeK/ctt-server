package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.auth.infrastructure.security.PublicApiEndpointRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration.
 *
 * <p>Provides security-related beans and configures the security filter chain with "Secure by
 * Default" pattern. All endpoints require authentication unless explicitly marked with @PublicApi.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final PublicApiEndpointRegistry publicApiRegistry;

    public SecurityConfig(PublicApiEndpointRegistry publicApiRegistry) {
        this.publicApiRegistry = publicApiRegistry;
    }

    /**
     * Configures the security filter chain with stateless session management.
     *
     * <p>Implements "Secure by Default":
     *
     * <ul>
     *   <li>All endpoints require authentication by default
     *   <li>Only @PublicApi marked endpoints are whitelisted
     *   <li>CSRF disabled (stateless API architecture)
     *   <li>Session management set to STATELESS (JWT-based)
     * </ul>
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(publicApiRegistry.getPublicUrls())
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated());

        return http.build();
    }

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
