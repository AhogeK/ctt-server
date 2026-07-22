package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.auth.apikey.crypto.ApiKeyHasher;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Bearer token resolver that skips non-JWT credentials so the {@link
 * com.ahogek.cttserver.auth.apikey.client.ApiKeyAuthenticationFilter} can take over.
 *
 * <p>Spring Security's default {@code DefaultBearerTokenResolver} extracts the value after the
 * configured header prefix in the {@code Authorization} header and forwards it to the JWT decoder.
 * When the value is an API key (prefix {@link ApiKeyHasher#KEY_PREFIX_MARKER}) instead of a JWT,
 * the decoder throws and the request is rejected with {@code AUTH_003} before the API key filter
 * has a chance to handle it.
 *
 * <p>This resolver returns {@code null} for non-JWT credentials, signalling the bearer filter to
 * bypass and let {@code ApiKeyAuthenticationFilter} authenticate the request instead.
 *
 * <p>The header prefix and API key prefix are sourced from {@link SecurityProperties} and {@link
 * ApiKeyHasher} respectively so the resolver stays in lockstep with the API key filter.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-21
 */
public final class ApiKeyAwareBearerTokenResolver implements BearerTokenResolver {

    private final String bearerPrefix;
    private final String apiKeyPrefix;

    public ApiKeyAwareBearerTokenResolver(SecurityProperties securityProperties) {
        this.bearerPrefix = securityProperties.apiKey().headerPrefix() + " ";
        this.apiKeyPrefix = ApiKeyHasher.KEY_PREFIX_MARKER;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(bearerPrefix)) {
            return null;
        }
        String token = header.substring(bearerPrefix.length()).trim();
        if (token.isEmpty() || token.startsWith(apiKeyPrefix)) {
            return null;
        }
        return token;
    }
}
