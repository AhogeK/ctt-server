package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.crypto.AesGcmTokenEncryptor;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenKeyRotationService}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-05
 */
@ExtendWith(MockitoExtension.class)
class TokenKeyRotationServiceTest {

    // Test keys (Base64-encoded 32 bytes)
    private static final String OLD_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String NEW_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=";

    @Mock private UserOAuthAccountRepository oauthAccountRepository;

    private TokenKeyRotationService rotationService;

    @BeforeEach
    void setUp() {
        rotationService = new TokenKeyRotationService(oauthAccountRepository);
    }

    @Nested
    @DisplayName("rotateKeys")
    class RotateKeys {

        @Test
        @DisplayName("should rotate tokens for all accounts")
        void shouldRotateTokensForAllAccounts() {
            // Given
            AesGcmTokenEncryptor oldEncryptor = new AesGcmTokenEncryptor(OLD_KEY);
            AesGcmTokenEncryptor newEncryptor = new AesGcmTokenEncryptor(NEW_KEY);

            // Create accounts with properly encrypted tokens
            UserOAuthAccount account1 =
                    createAccountWithTokens(
                            oldEncryptor.encrypt("token1"), oldEncryptor.encrypt("refresh1"));
            UserOAuthAccount account2 =
                    createAccountWithTokens(
                            oldEncryptor.encrypt("token2"), oldEncryptor.encrypt("refresh2"));

            when(oauthAccountRepository.findAll()).thenReturn(List.of(account1, account2));
            when(oauthAccountRepository.save(any())).thenReturn(null);

            // When
            int rotated = rotationService.rotateKeys(oldEncryptor, newEncryptor);

            // Then
            assertThat(rotated).isEqualTo(2);

            // Verify tokens are re-encrypted with new key
            ArgumentCaptor<UserOAuthAccount> captor =
                    ArgumentCaptor.forClass(UserOAuthAccount.class);
            verify(oauthAccountRepository, times(2)).save(captor.capture());

            List<UserOAuthAccount> savedAccounts = captor.getAllValues();
            assertThat(newEncryptor.decrypt(savedAccounts.get(0).getAccessToken()))
                    .isEqualTo("token1");
            assertThat(newEncryptor.decrypt(savedAccounts.get(0).getRefreshToken()))
                    .isEqualTo("refresh1");
            assertThat(newEncryptor.decrypt(savedAccounts.get(1).getAccessToken()))
                    .isEqualTo("token2");
            assertThat(newEncryptor.decrypt(savedAccounts.get(1).getRefreshToken()))
                    .isEqualTo("refresh2");
        }

        @Test
        @DisplayName("should handle null tokens gracefully")
        void shouldHandleNullTokensGracefully() {
            // Given
            AesGcmTokenEncryptor oldEncryptor = new AesGcmTokenEncryptor(OLD_KEY);
            AesGcmTokenEncryptor newEncryptor = new AesGcmTokenEncryptor(NEW_KEY);

            UserOAuthAccount account = createAccountWithTokens(null, null);

            when(oauthAccountRepository.findAll()).thenReturn(List.of(account));
            when(oauthAccountRepository.save(any())).thenReturn(null);

            // When
            int rotated = rotationService.rotateKeys(oldEncryptor, newEncryptor);

            // Then
            assertThat(rotated).isEqualTo(1);
            verify(oauthAccountRepository).save(account);
        }

        @Test
        @DisplayName("should throw exception when old encryptor is null")
        void shouldThrowExceptionWhenOldEncryptorIsNull() {
            // Given
            AesGcmTokenEncryptor newEncryptor = new AesGcmTokenEncryptor(NEW_KEY);

            // When/Then
            assertThatThrownBy(() -> rotationService.rotateKeys(null, newEncryptor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Both old and new encryptors must be provided");
        }

        @Test
        @DisplayName("should throw exception when new encryptor is null")
        void shouldThrowExceptionWhenNewEncryptorIsNull() {
            // Given
            AesGcmTokenEncryptor oldEncryptor = new AesGcmTokenEncryptor(OLD_KEY);

            // When/Then
            assertThatThrownBy(() -> rotationService.rotateKeys(oldEncryptor, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Both old and new encryptors must be provided");
        }

        @Test
        @DisplayName("should throw exception when rotation fails")
        void shouldThrowExceptionWhenRotationFails() {
            // Given
            AesGcmTokenEncryptor oldEncryptor = new AesGcmTokenEncryptor(OLD_KEY);
            AesGcmTokenEncryptor newEncryptor = new AesGcmTokenEncryptor(NEW_KEY);

            UserOAuthAccount account = createAccountWithTokens("invalid-ciphertext", null);

            when(oauthAccountRepository.findAll()).thenReturn(List.of(account));

            // When/Then
            assertThatThrownBy(() -> rotationService.rotateKeys(oldEncryptor, newEncryptor))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Key rotation failed");
        }
    }

    private UserOAuthAccount createAccountWithTokens(String accessToken, String refreshToken) {
        User user = new User();
        user.setId(UUID.randomUUID());

        UserOAuthAccount account = new UserOAuthAccount();
        account.setId(UUID.randomUUID());
        account.setUser(user);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        return account;
    }
}
