package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.fixtures.TokenFixtures;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseRepositoryTest
@DisplayName("EmailVerificationTokenRepository")
class EmailVerificationTokenRepositoryTest {

    @Autowired TestEntityManager em;

    @Autowired EmailVerificationTokenRepository repository;

    @Nested
    @DisplayName("findByTokenHash")
    class FindByTokenHash {

        @Test
        @DisplayName("should find token when tokenHash exists")
        void shouldFindByTokenHash_whenTokenExists() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());
            String tokenHash = "abc123hash";
            EmailVerificationToken token =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash(tokenHash)
                            .build();
            em.persistFlushFind(token);

            // When
            Optional<EmailVerificationToken> result = repository.findByTokenHash(tokenHash);

            // Then
            assertThat(result)
                    .isPresent()
                    .hasValueSatisfying(
                            t -> {
                                assertThat(t.getTokenHash()).isEqualTo(tokenHash);
                                assertThat(t.getUserId()).isEqualTo(user.getId());
                            });
        }

        @Test
        @DisplayName("should return empty when tokenHash not found")
        void shouldReturnEmpty_whenTokenHashNotFound() {
            // Given
            String nonExistentHash = "nonexistenthash123";

            // When
            Optional<EmailVerificationToken> result = repository.findByTokenHash(nonExistentHash);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByUserIdOrderByCreatedAtDesc")
    class FindFirstByUserIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("should find latest token by userId")
        void shouldFindLatestTokenByUserId() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());
            Instant now = Instant.now();

            EmailVerificationToken olderToken =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("older-hash")
                            .build();
            olderToken.setCreatedAt(now.minusSeconds(1));
            em.persistFlushFind(olderToken);

            EmailVerificationToken newerToken =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("newer-hash")
                            .build();
            newerToken.setCreatedAt(now);
            em.persistFlushFind(newerToken);

            // When
            Optional<EmailVerificationToken> result =
                    repository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());

            // Then
            assertThat(result)
                    .isPresent()
                    .hasValueSatisfying(
                            t -> {
                                assertThat(t.getTokenHash()).isEqualTo("newer-hash");
                                assertThat(t.getUserId()).isEqualTo(user.getId());
                            });
        }

        @Test
        @DisplayName("should return empty when user has no tokens")
        void shouldReturnEmpty_whenUserHasNoTokens() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            Optional<EmailVerificationToken> result =
                    repository.findFirstByUserIdOrderByCreatedAtDesc(nonExistentUserId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserId {

        @Test
        @DisplayName("should delete all tokens by userId")
        void shouldDeleteAllTokensByUserId() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());

            EmailVerificationToken token1 =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("hash1")
                            .build();
            em.persistFlushFind(token1);

            EmailVerificationToken token2 =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("hash2")
                            .build();
            em.persistFlushFind(token2);

            // When
            long deletedCount = repository.deleteByUserId(user.getId());

            // Then
            assertThat(deletedCount).isEqualTo(2);
            assertThat(repository.findByTokenHash("hash1")).isEmpty();
            assertThat(repository.findByTokenHash("hash2")).isEmpty();
        }

        @Test
        @DisplayName("should return zero when no tokens to delete")
        void shouldReturnZero_whenNoTokensToDelete() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            long deletedCount = repository.deleteByUserId(nonExistentUserId);

            // Then
            assertThat(deletedCount).isZero();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndPurpose")
    class FindByUserIdAndPurpose {

        @Test
        @DisplayName("should return tokens when user and purpose match")
        void shouldReturnTokens_whenUserAndPurposeMatch() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());

            EmailVerificationToken token1 =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("hash1")
                            .purpose(EmailVerificationToken.PURPOSE_REGISTER_VERIFY)
                            .build();
            em.persistFlushFind(token1);

            EmailVerificationToken token2 =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("hash2")
                            .purpose(EmailVerificationToken.PURPOSE_REGISTER_VERIFY)
                            .build();
            em.persistFlushFind(token2);

            // When
            var result =
                    repository.findByUserIdAndPurpose(
                            user.getId(), EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(result)
                    .hasSize(2)
                    .extracting(EmailVerificationToken::getUserId)
                    .allMatch(id -> id.equals(user.getId()));
        }

        @Test
        @DisplayName("should return empty list when no match")
        void shouldReturnEmptyList_whenNoMatch() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            var result =
                    repository.findByUserIdAndPurpose(
                            nonExistentUserId, EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter by purpose")
        void shouldFilterByPurpose() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());

            EmailVerificationToken registerToken =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("register-hash")
                            .purpose(EmailVerificationToken.PURPOSE_REGISTER_VERIFY)
                            .build();
            em.persistFlushFind(registerToken);

            EmailVerificationToken changeEmailToken =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .tokenHash("change-email-hash")
                            .purpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL)
                            .build();
            em.persistFlushFind(changeEmailToken);

            // When
            var result =
                    repository.findByUserIdAndPurpose(
                            user.getId(), EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(result)
                    .hasSize(1)
                    .first()
                    .satisfies(
                            t ->
                                    assertThat(t.getPurpose())
                                            .isEqualTo(
                                                    EmailVerificationToken
                                                            .PURPOSE_REGISTER_VERIFY));
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndPurposeAndConsumedAtIsNull")
    class ExistsByUserIdAndPurposeAndConsumedAtIsNull {

        @Test
        @DisplayName("should return true when unconsumed token exists")
        void shouldReturnTrue_whenUnconsumedTokenExists() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());

            EmailVerificationToken token =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .purpose(EmailVerificationToken.PURPOSE_REGISTER_VERIFY)
                            .consumedAt(null)
                            .build();
            em.persistFlushFind(token);

            // When
            boolean exists =
                    repository.existsByUserIdAndPurposeAndConsumedAtIsNull(
                            user.getId(), EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when only consumed tokens exist")
        void shouldReturnFalse_whenOnlyConsumedTokensExist() {
            // Given
            User user = em.persistFlushFind(UserFixtures.regularUser().build());

            EmailVerificationToken token =
                    TokenFixtures.emailVerificationTokenBuilder()
                            .userId(user.getId())
                            .purpose(EmailVerificationToken.PURPOSE_REGISTER_VERIFY)
                            .consumedAt(Instant.now())
                            .build();
            em.persistFlushFind(token);

            // When
            boolean exists =
                    repository.existsByUserIdAndPurposeAndConsumedAtIsNull(
                            user.getId(), EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false when no tokens exist")
        void shouldReturnFalse_whenNoTokensExist() {
            // Given
            UUID nonExistentUserId = UUID.randomUUID();

            // When
            boolean exists =
                    repository.existsByUserIdAndPurposeAndConsumedAtIsNull(
                            nonExistentUserId, EmailVerificationToken.PURPOSE_REGISTER_VERIFY);

            // Then
            assertThat(exists).isFalse();
        }
    }
}
