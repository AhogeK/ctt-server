package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    @DisplayName("should key the marker on both the rule generation and the vocabulary version")
    void shouldKeyMarker_onRulesAndVocabulary() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findAllIds()).thenReturn(List.of());

        backfill.backfillScores();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), any());

        // Both inputs are in the key on purpose: a hand-bumped generation covers a changed formula,
        // and the vocabulary version covers canonical names changing underneath the scores. Any
        // mechanism that dropped one would let a needed recompute be skipped as "already done".
        assertThat(key.getValue())
                .contains("recompute")
                .containsPattern("g\\d+")
                .containsPattern("vocab\\d+");
    }
}
