package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.user.entity.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * JWT Access Token provider.
 *
 * <p>Issues JWT access tokens using Nimbus JOSE + HMAC-SHA256. Tokens contain standard claims (iss,
 * sub, iat, exp) plus custom claims (email).
 *
 * <p><strong>Security Notes:</strong>
 *
 * <ul>
 *   <li>Access tokens are short-lived (default 15 minutes)
 *   <li>Payload is Base64Url encoded, NOT encrypted - never include sensitive data (password, PII)
 *   <li>The `sub` claim maps to Authentication.getName() for @AuthenticationPrincipal support
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
@Service
public class JwtTokenProvider {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties.JwtProperties jwtProps;

    public JwtTokenProvider(JwtEncoder jwtEncoder, SecurityProperties securityProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProps = securityProperties.jwt();
    }

    /**
     * Generates a JWT access token for the specified user.
     *
     * @param user the user entity
     * @return the encoded JWT token string
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProps.accessTokenTtl());

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(jwtProps.issuer())
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("status", user.getStatus().name())
                        .claim("authorities", DEFAULT_ROLE)
                        .claim("termsVersion", user.getTermsVersion() != null ? user.getTermsVersion() : "")
                        .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
