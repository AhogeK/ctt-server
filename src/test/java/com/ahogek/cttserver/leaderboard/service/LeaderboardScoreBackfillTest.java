package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Leaderboard score backfill")
class LeaderboardScoreBackfillTest {

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    private UserRepository userRepository;
    private LeaderboardService leaderboardService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private LeaderboardScoreBackfill backfill;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        leaderboardService = mock(LeaderboardService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        backfill =
                new LeaderboardScoreBackfill(
                        userRepository,
                        leaderboardService,
                        redisTemplate,
                        new LanguageVocabulary(new ObjectMapper()));
    }

    @Test
    @DisplayName("should recompute every user and mark the sweep done")
    void shouldRecomputeAll_whenMarkerAbsent() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findAllIds()).thenReturn(List.of(first, second));

        backfill.backfillScores();

        verify(leaderboardService).updateUserScores(first);
        verify(leaderboardService).updateUserScores(second);
        verify(valueOps).set(anyString(), eq("done"));
    }

    @Test
    @DisplayName("should do nothing when the marker says scores are current")
    void shouldDoNothing_whenMarkerPresent() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        backfill.backfillScores();

        verifyNoInteractions(leaderboardService);
        verify(userRepository, never()).findAllIds();
    }

    @Test
    @DisplayName("should continue past a user whose history cannot be read")
    void shouldContinue_whenOneUserFails() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findAllIds()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("unreadable history"))
                .when(leaderboardService)
                .updateUserScores(first);

        backfill.backfillScores();

        // One unreadable history must not abandon the other users, and the sweep still completes:
        // leaving the marker unset would repeat the whole sweep daily for a single bad row.
        verify(leaderboardService).updateUserScores(second);
        verify(valueOps).set(anyString(), eq("done"));
    }

    @Test
    @DisplayName(
            "should key the marker on the rule generation, the score matrix and the vocabulary")
    void shouldKeyMarker_onRulesMatrixAndVocabulary() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findAllIds()).thenReturn(List.of());

        backfill.backfillScores();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), any());

        // All three inputs are in the key on purpose: the generation covers a changed formula, the
        // matrix fingerprint covers a dimension or period appearing, and the vocabulary version
        // covers canonical names changing underneath the scores. Dropping any one would let a
        // needed recompute be skipped as "already done".
        assertThat(key.getValue())
                .contains("recompute")
                .contains(":g4:")
                .containsPattern("cov[0-9a-f]{8}")
                .containsPattern("vocab\\d+");
    }

    @Test
    @DisplayName("should not reuse the marker written before the score matrix grew")
    void shouldNotReuseMarker_whenTheScoreMatrixGrew() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findAllIds()).thenReturn(List.of());

        backfill.backfillScores();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), any());

        // The marker this release replaced: it was written when STREAK was lifetime-only. Reusing
        // it skipped every user who had not pushed since, leaving the windowed boards empty while
        // the lifetime board looked healthy.
        assertThat(key.getValue()).isNotEqualTo("leaderboard:recompute:g2:vocab2");
    }

    @Test
    @DisplayName("should move the fingerprint when the score matrix changes")
    void shouldMoveFingerprint_whenTheMatrixChanges() {
        List<String> pairs = LeaderboardScoreBackfill.legalPairs();

        assertThat(LeaderboardScoreBackfill.coverageToken(pairs))
                .isEqualTo(LeaderboardScoreBackfill.coverageToken(pairs));

        List<String> narrowed = new ArrayList<>(pairs);
        narrowed.remove("STREAK:WEEK");

        assertThat(LeaderboardScoreBackfill.coverageToken(narrowed))
                .isNotEqualTo(LeaderboardScoreBackfill.coverageToken(pairs));
    }

    @Test
    @DisplayName("should ignore the order the matrix is enumerated in")
    void shouldIgnorePairOrder() {
        List<String> pairs = new ArrayList<>(LeaderboardScoreBackfill.legalPairs());
        Collections.reverse(pairs);

        assertThat(LeaderboardScoreBackfill.coverageToken(pairs))
                .isEqualTo(
                        LeaderboardScoreBackfill.coverageToken(
                                LeaderboardScoreBackfill.legalPairs()));
    }
}
