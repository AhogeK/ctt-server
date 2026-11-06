package com.ahogek.cttserver.leaderboard.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Leaderboard recompute queue")
class LeaderboardRecomputeQueueTest {

    private final UUID userId = UUID.randomUUID();

    private LeaderboardService leaderboardService;
    private StringRedisTemplate redisTemplate;
    private SetOperations<String, String> setOps;
    private LeaderboardRecomputeQueue queue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        leaderboardService = mock(LeaderboardService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        queue = new LeaderboardRecomputeQueue(redisTemplate, leaderboardService);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should write the mark immediately when no transaction is active")
    void shouldWriteTheMarkImmediately_whenNoTransactionIsActive() {
        queue.markDirty(userId);

        verify(setOps).add(LeaderboardRecomputeQueue.DIRTY_KEY, userId.toString());
    }

    @Test
    @DisplayName("should hold the mark until commit when a transaction is active")
    void shouldHoldTheMarkUntilCommit_whenATransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();

        queue.markDirty(userId);
        verify(setOps, never()).add(LeaderboardRecomputeQueue.DIRTY_KEY, userId.toString());

        commit();

        verify(setOps).add(LeaderboardRecomputeQueue.DIRTY_KEY, userId.toString());
    }

    @Test
    @DisplayName("should not queue anything when the transaction rolls back")
    void shouldNotQueueAnything_whenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        queue.markDirty(userId);
        // Spring invokes afterCompletion on rollback and never afterCommit, which is exactly the
        // branch this test pins: a push that failed wrote no sessions, so there is nothing to
        // recompute.
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(setOps, never()).add(LeaderboardRecomputeQueue.DIRTY_KEY, userId.toString());
    }

    @Test
    @DisplayName("should recompute every popped user when draining")
    void shouldRecomputeEveryPoppedUser_whenDraining() {
        UUID other = UUID.randomUUID();
        when(setOps.pop(LeaderboardRecomputeQueue.DIRTY_KEY, 100))
                .thenReturn(List.of(userId.toString(), other.toString()));

        queue.drainDirty();

        verify(leaderboardService).updateUserScores(userId);
        verify(leaderboardService).updateUserScores(other);
    }

    @Test
    @DisplayName("should do nothing when no user is marked")
    void shouldDoNothing_whenNoUserIsMarked() {
        when(setOps.pop(LeaderboardRecomputeQueue.DIRTY_KEY, 100)).thenReturn(List.of());

        queue.drainDirty();

        verifyNoInteractions(leaderboardService);
    }

    @Test
    @DisplayName("should skip a malformed mark instead of aborting the whole drain")
    void shouldSkipAMalformedMark_insteadOfAbortingTheWholeDrain() {
        when(setOps.pop(LeaderboardRecomputeQueue.DIRTY_KEY, 100))
                .thenReturn(List.of("not-a-uuid", userId.toString()));

        assertThatCode(queue::drainDirty).doesNotThrowAnyException();

        // Exactly one recompute, and it is the parseable id: the malformed mark never reached the
        // service and did not stop the user behind it from being recomputed.
        verify(leaderboardService).updateUserScores(userId);
    }

    /** Fires the commit callbacks Spring would fire, without needing a real transaction manager. */
    private void commit() {
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
