package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenProviderTest {

    private static final String TEST_ISSUER = "ctt-identity-provider";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String TEST_TOKEN_VALUE =
            "eyJhbGciOiJIUzI1NiJ9.test-payload.test-signature";

    @Mock private JwtEncoder jwtEncoder;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(jwtProps.issuer()).thenReturn(TEST_ISSUER);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);

        provider = new JwtTokenProvider(jwtEncoder, securityProperties);
    }

    @Test
    @DisplayName("generateAccessToken should return encoded token value")
    void generateAccessToken_shouldReturnEncodedTokenValue() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        String token = provider.generateAccessToken(user);

        // Then
        assertThat(token).isEqualTo(TEST_TOKEN_VALUE);
    }

    @Test
    @DisplayName("generateAccessToken should use correct issuer claim")
    void generateAccessToken_shouldUseCorrectIssuerClaim() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat((String) claims.getClaim("iss")).isEqualTo(TEST_ISSUER);
    }

    @Test
    @DisplayName("generateAccessToken should set subject to userId")
    void generateAccessToken_shouldSetSubjectToUserId() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    @DisplayName("generateAccessToken should include email claim")
    void generateAccessToken_shouldIncludeEmailClaim() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat((String) claims.getClaim("email")).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("generateAccessToken should set expiration based on accessTokenTtl")
    void generateAccessToken_shouldSetExpirationBasedOnTtl() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);
        Instant beforeCall = Instant.now();

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        Instant afterCall = Instant.now();

        assertThat(claims.getIssuedAt()).isBetween(beforeCall, afterCall);
        assertThat(claims.getExpiresAt())
                .isBetween(
                        beforeCall.plus(ACCESS_TOKEN_TTL).minus(1, ChronoUnit.SECONDS),
                        afterCall.plus(ACCESS_TOKEN_TTL).plus(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("generateAccessToken should include all required claims")
    void generateAccessToken_shouldIncludeAllRequiredClaims() {
        // Given
        User user = createTestUser();
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();

        assertThat((Object) claims.getClaim("iss")).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiresAt()).isNotNull();
        assertThat(claims.getSubject()).isNotNull();
        assertThat((Object) claims.getClaim("email")).isNotNull();
    }

    @Test
    @DisplayName("should include status and authorities claims in access token")
    void shouldIncludeStatusAndAuthoritiesClaims_inAccessToken() {
        // Given
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "email", "test@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.ACTIVE);

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn(TEST_TOKEN_VALUE);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        provider.generateAccessToken(user);

        // Then
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat((String) claims.getClaim("status")).isEqualTo("ACTIVE");
        assertThat((String) claims.getClaim("authorities")).isEqualTo("ROLE_USER");
    }

    private User createTestUser() {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        return user;
    }
}
