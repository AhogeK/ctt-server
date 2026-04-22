package com.ahogek.cttserver.auth.oauth.config;

import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;

import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts lowercase provider strings (e.g., "github") to OAuthProvider enum constants.
 *
 * <p>Enables case-insensitive path variable binding for OAuth endpoints.
 */
@Component
public class OAuthProviderConverter implements Converter<String, OAuthProvider> {

    @Override
    public OAuthProvider convert(@NonNull String source) {
        return OAuthProvider.fromValue(source);
    }
}
