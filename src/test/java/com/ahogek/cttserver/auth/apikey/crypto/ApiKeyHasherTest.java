package com.ahogek.cttserver.auth.apikey.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiKeyHasher}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@DisplayName("ApiKeyHasher - API Key Generation & Hashing")
class ApiKeyHasherTest {

    private static final Pattern RAW_KEY_PATTERN =
            Pattern.compile("^cttak_[A-Za-z0-9_-]{8}_[A-Za-z0-9_-]{32}$");

    private static final int ENTROPY_ITERATIONS = 1_000;

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Nested
    @DisplayName("generateRawKey()")
    class GenerateRawKey {

        @Test
        @DisplayName("should produce raw key matching cttak_<prefix>_<secret> format")
        void shouldProduceRawKeyMatchingFormat() {
            String rawKey = hasher.generateRawKey();

            assertThat(rawKey).matches(RAW_KEY_PATTERN);
        }

        @Test
        @DisplayName("should expose exactly 8-char prefix segment between markers")
        void shouldExposeEightCharPrefixSegment() {
            String rawKey = hasher.generateRawKey();
            String marker = "cttak_";
            String prefixSegment = rawKey.substring(marker.length(), marker.length() + 8);
            String secretSegment = rawKey.substring(marker.length() + 8 + 1);

            assertThat(prefixSegment).hasSize(8);
            assertThat(secretSegment).hasSize(32);
            assertThat(rawKey).isEqualTo(marker + prefixSegment + "_" + secretSegment);
        }

        @Test
        @DisplayName("should use URL-safe Base64 alphabet only")
        void shouldUseUrlSafeBase64Alphabet() {
            String rawKey = hasher.generateRawKey();
            String alphabetPayload = rawKey.substring("cttak_".length());

            assertThat(alphabetPayload).matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("should produce distinct keys across many invocations")
        void shouldProduceDistinctKeysAcrossInvocations() {
            Set<String> keys = new HashSet<>(ENTROPY_ITERATIONS);

            for (int i = 0; i < ENTROPY_ITERATIONS; i++) {
                keys.add(hasher.generateRawKey());
            }

            assertThat(keys).hasSize(ENTROPY_ITERATIONS);
        }
    }

    @Nested
    @DisplayName("hashKey(String)")
    class HashKey {

        @Test
        @DisplayName("should produce identical SHA-256 hash for same raw key input")
        void shouldProduceIdenticalHashForSameRawKey() {
            String rawKey = "cttak_AbCdEfGh_1234567890abcdefghijklmnopqrstuv";

            String firstHash = hasher.hashKey(rawKey);
            String secondHash = hasher.hashKey(rawKey);

            assertThat(firstHash).isEqualTo(secondHash);
        }

        @Test
        @DisplayName("should produce 64-character lowercase hex SHA-256 hash")
        void shouldProduceSixtyFourCharHexHash() {
            String hash = hasher.hashKey("cttak_AbCdEfGh_1234567890abcdefghijklmnopqrstuv");

            assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("should produce different hash for different raw keys")
        void shouldProduceDifferentHashForDifferentRawKeys() {
            String hashA = hasher.hashKey("cttak_AAAAAAAA_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            String hashB = hasher.hashKey("cttak_BBBBBBBB_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

            assertThat(hashA).isNotEqualTo(hashB);
        }

        @Test
        @DisplayName("should match known SHA-256 vector for the literal raw key")
        void shouldMatchKnownSha256Vector() {
            String hash = hasher.hashKey("cttak_AbCdEfGh_1234567890abcdefghijklmnopqrstuv");

            assertThat(hash)
                    .isEqualTo("b6f539e5af90c53248f3ccf9fd094028ea789a8a35dcd71ec60cccde71707532");
        }
    }

    @Nested
    @DisplayName("Integration: generateRawKey() + hashKey()")
    class GenerateAndHashIntegration {

        @Test
        @DisplayName("should hash any generated raw key consistently on re-hash")
        void shouldHashGeneratedRawKeyConsistently() {
            String rawKey = hasher.generateRawKey();

            String firstHash = hasher.hashKey(rawKey);
            String secondHash = hasher.hashKey(rawKey);

            assertThat(firstHash).isEqualTo(secondHash);
            assertThat(firstHash).hasSize(64);
        }
    }
}
