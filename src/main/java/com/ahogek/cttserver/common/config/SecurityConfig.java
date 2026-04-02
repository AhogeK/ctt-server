package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.auth.infrastructure.security.JwtAuthenticationEntryPoint;
import com.ahogek.cttserver.auth.infrastructure.security.PublicApiEndpointRegistry;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration with OWASP-compliant headers.
 *
 * <p>Provides security-related beans and configures the security filter chain with:
 *
 * <ul>
 *   <li>"Secure by Default" pattern (all endpoints require authentication)
 *   <li>OWASP security headers (XSS protection, clickjacking prevention, HSTS)
 *   <li>Stateless session management (JWT-based)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final PublicApiEndpointRegistry publicApiRegistry;
    private final SecurityProperties securityProperties;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(
            PublicApiEndpointRegistry publicApiRegistry,
            SecurityProperties securityProperties,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.publicApiRegistry = publicApiRegistry;
        this.securityProperties = securityProperties;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    /**
     * Configures the security filter chain with OWASP security headers and stateless session
     * management.
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
     * <p>Security Headers (OWASP recommended):
     *
     * <ul>
     *   <li>X-Content-Type-Options: nosniff (prevent MIME sniffing)
     *   <li>X-XSS-Protection: 1; mode=block (XSS filter)
     *   <li>X-Frame-Options: DENY (prevent clickjacking)
     *   <li>Strict-Transport-Security: max-age=31536000; includeSubDomains (HSTS)
     *   <li>Content-Security-Policy: default-src 'self' (CSP)
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
                .headers(
                        headers ->
                                headers.contentTypeOptions(_ -> {})
                                        .xssProtection(
                                                xss ->
                                                        xss.headerValue(
                                                                XXssProtectionHeaderWriter
                                                                        .HeaderValue
                                                                        .ENABLED_MODE_BLOCK))
                                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                        .contentSecurityPolicy(
                                                csp -> csp.policyDirectives("default-src 'self'")))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(Customizer.withDefaults())
                                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .exceptionHandling(
                        exceptions ->
                                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(publicApiRegistry.getPublicUrls())
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated());

        return http.build();
    }

    /**
     * Provides BCrypt password encoder with configured strength.
     *
     * <p>BCrypt strength (log rounds) is configured via {@code
     * ctt.security.password.bcrypt-rounds}, defaulting to 12. Higher values increase computation
     * time for password hashing, making rainbow table attacks more expensive.
     *
     * @return the password encoder configured with bcrypt rounds
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.password().bcryptRounds());
    }
}
