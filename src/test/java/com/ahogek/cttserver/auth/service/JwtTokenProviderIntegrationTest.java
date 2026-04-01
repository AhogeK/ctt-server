package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for JWT token encoding and decoding with HS256 algorithm.
 *
 * <p>Verifies that JwtTokenProvider and JwtDecoder work together correctly using
 * HMAC-SHA256 (HS256) algorithm. This test ensures the entire JWT pipeline from
 * token generation to validation operates with the expected algorithm and claims.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-02
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JwtTokenProviderIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("should encode and decode JWT token with HS256 algorithm")
    void shouldEncodeAndDecodeWithHS256() {
        // Given: Create test user (in-memory, not persisted)
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setPasswordHash("hashed-password");

        // When: Generate access token
        String token = jwtTokenProvider.generateAccessToken(user);

        // And: Decode the token
        Jwt decoded = jwtDecoder.decode(token);

        // Then: Verify algorithm is HS256
        assertThat(decoded.getHeaders().get("alg"))
                .as("JWT header algorithm must be HS256")
                .isEqualTo("HS256");

        // And: Verify claims are preserved
        assertThat(decoded.getSubject())
                .as("Subject claim should match user ID")
                .isEqualTo(user.getId().toString());

        assertThat(decoded.getClaimAsString("email"))
                .as("Email claim should match user email")
                .isEqualTo(user.getEmail());

        assertThat(decoded.getClaimAsString("iss"))
                .as("Issuer claim should contain 'ctt-server'")
                .contains("ctt");

        assertThat(decoded.getExpiresAt())
                .as("Expiration time should be after issued time")
                .isAfter(decoded.getIssuedAt());

        assertThat(decoded.getIssuedAt())
                .as("Issued time should not be null")
                .isNotNull();
    }
}