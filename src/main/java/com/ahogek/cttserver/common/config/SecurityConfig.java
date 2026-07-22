package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.auth.apikey.client.ApiKeyAuthenticationFilter;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.infrastructure.security.JwtAuthenticationEntryPoint;
import com.ahogek.cttserver.auth.infrastructure.security.JwtToCurrentUserConverter;
import com.ahogek.cttserver.auth.infrastructure.security.PublicApiEndpointRegistry;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.web.cors.CorsConfigurationSource;

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
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final PublicApiEndpointRegistry publicApiRegistry;
    private final SecurityProperties securityProperties;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtToCurrentUserConverter jwtToCurrentUserConverter;
    private final TermsCheckFilter termsCheckFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(
            PublicApiEndpointRegistry publicApiRegistry,
            SecurityProperties securityProperties,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtToCurrentUserConverter jwtToCurrentUserConverter,
            TermsCheckFilter termsCheckFilter,
            CorsConfigurationSource corsConfigurationSource,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.publicApiRegistry = publicApiRegistry;
        this.securityProperties = securityProperties;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtToCurrentUserConverter = jwtToCurrentUserConverter;
        this.termsCheckFilter = termsCheckFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
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
     *   <li>CSRF protection via Double Submit Cookie pattern (exempt for public endpoints)
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
     *   <li>Content-Security-Policy: restrict sources, allow hCaptcha (CSP)
     *   <li>Referrer-Policy: no-referrer (prevent Referrer leakage)
     * </ul>
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers(publicApiRegistry.getPublicUrls())
                                        .ignoringRequestMatchers(
                                                request -> {
                                                    String authHeader =
                                                            request.getHeader(
                                                                    securityProperties
                                                                            .apiKey()
                                                                            .headerName());
                                                    return authHeader != null
                                                            && authHeader.startsWith(
                                                                    securityProperties
                                                                                    .apiKey()
                                                                                    .headerPrefix()
                                                                            + " ");
                                                }))
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
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; "
                                                                        + "script-src 'self' 'https://hcaptcha.com' 'https://*.hcaptcha.com'; "
                                                                        + "frame-src 'self' 'https://*.hcaptcha.com'; "
                                                                        + "connect-src 'self' 'https://*.hcaptcha.com'; "
                                                                        + "img-src 'self' data: 'https://*.hcaptcha.com'; "
                                                                        + "style-src 'self' 'unsafe-inline'; "
                                                                        + "font-src 'self' data:; "
                                                                        + "object-src 'none'; "
                                                                        + "base-uri 'self'; "
                                                                        + "form-action 'self'; "
                                                                        + "frame-ancestors 'none'"))
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicy.NO_REFERRER)))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .addFilterAfter(termsCheckFilter, SecurityContextHolderAwareRequestFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.bearerTokenResolver(apiKeyBearerTokenResolver())
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtToCurrentUserConverter))
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
     * @return the PasswordEncoder configured with bcrypt rounds
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.password().bcryptRounds());
    }

    /**
     * Provides a {@link BearerTokenResolver} that defers API key credentials to {@link
     * com.ahogek.cttserver.auth.apikey.client.ApiKeyAuthenticationFilter} rather than feeding them
     * into the JWT decoder.
     */
    @Bean
    public BearerTokenResolver apiKeyBearerTokenResolver() {
        return new ApiKeyAwareBearerTokenResolver(securityProperties);
    }
}
