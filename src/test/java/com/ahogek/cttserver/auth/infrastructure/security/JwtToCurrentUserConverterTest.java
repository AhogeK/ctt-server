package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtToCurrentUserConverterTest {

    private static final String TEST_TOKEN_VALUE = "test.jwt.token";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_EMAIL = "test@example.com";

    private final JwtToCurrentUserConverter converter = new JwtToCurrentUserConverter();

    @Test
    @DisplayName("convert should throw IllegalArgumentException when JWT is null")
    void convert_shouldThrowException_whenJwtIsNull() {
        // When & Then
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT token cannot be null");
    }

    @Test
    @DisplayName("convert should throw IllegalArgumentException when subject is null")
    void convert_shouldThrowException_whenSubjectIsNull() {
        // Given
        Jwt jwt = createMockJwt(Map.of(), null);

        // When & Then
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT subject (user ID) cannot be null");
    }

    @Test
    @DisplayName("convert should throw IllegalArgumentException when subject is malformed UUID")
    void convert_shouldThrowException_whenSubjectIsMalformedUuid() {
        // Given
        Jwt jwt = createMockJwt(Map.of(), "not-a-uuid");

        // When & Then
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    @DisplayName("convert should create CurrentUser with valid JWT")
    void convert_shouldCreateCurrentUser_whenJwtIsValid() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of(
                                "email", TEST_EMAIL,
                                "status", "ACTIVE",
                                "authorities", "ROLE_USER"),
                        TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCredentials()).isEqualTo(TEST_TOKEN_VALUE);
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities())
                .containsExactly(new SimpleGrantedAuthority("ROLE_USER"));

        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.id()).isEqualTo(TEST_USER_ID);
        assertThat(currentUser.email()).isEqualTo(TEST_EMAIL);
        assertThat(currentUser.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(currentUser.authorities()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("convert should use default ACTIVE status when status claim is missing")
    void convert_shouldUseDefaultStatus_whenStatusClaimMissing() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of("email", TEST_EMAIL, "authorities", "ROLE_USER"),
                        TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("convert should use empty authorities when authorities claim is missing")
    void convert_shouldUseEmptyAuthorities_whenAuthoritiesClaimMissing() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of("email", TEST_EMAIL, "status", "ACTIVE"), TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.authorities()).isEmpty();
        assertThat(result.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("convert should handle multiple authorities separated by comma")
    void convert_shouldHandleMultipleAuthorities() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of(
                                "email", TEST_EMAIL,
                                "status", "ACTIVE",
                                "authorities", "ROLE_USER,ROLE_ADMIN,READ_WRITE"),
                        TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.authorities())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "READ_WRITE");
        assertThat(result.getAuthorities())
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("READ_WRITE"));
    }

    @Test
    @DisplayName("convert should trim whitespace from authorities")
    void convert_shouldTrimWhitespaceFromAuthorities() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of(
                                "email", TEST_EMAIL,
                                "status", "ACTIVE",
                                "authorities", " ROLE_USER , ROLE_ADMIN "),
                        TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.authorities()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("convert should handle blank authorities as empty")
    void convert_shouldHandleBlankAuthoritiesAsEmpty() {
        // Given
        Jwt jwt =
                createMockJwt(
                        Map.of(
                                "email", TEST_EMAIL,
                                "status", "ACTIVE",
                                "authorities", "   "),
                        TEST_USER_ID.toString());

        // When
        UsernamePasswordAuthenticationToken result = converter.convert(jwt);

        // Then
        CurrentUser currentUser = (CurrentUser) result.getPrincipal();
        assertThat(currentUser.authorities()).isEmpty();
    }

    private Jwt createMockJwt(Map<String, Object> claims, String subject) {
        return Jwt.withTokenValue(TEST_TOKEN_VALUE)
                .header("alg", "HS256")
                .claim("sub", subject)
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
