package com.ahogek.cttserver.auth.config;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWT Cryptography Configuration.
 *
 * <p>Registers Nimbus-based JwtEncoder and JwtDecoder using symmetric encryption (HMAC-SHA256).
 *
 * <p><strong>Security Note:</strong> The secret key must be at least 32 characters (256 bits) to
 * avoid IllegalArgumentException during initialization.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private final SecurityProperties.JwtProperties jwtProps;

    public JwtConfig(SecurityProperties securityProperties) {
        this.jwtProps = securityProperties.jwt();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKey key =
                new SecretKeySpec(
                        jwtProps.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(key);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key =
                new SecretKeySpec(
                        jwtProps.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
