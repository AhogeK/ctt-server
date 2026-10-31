package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("Two-factor setup store")
class TwoFactorSetupStoreTest {

    @Autowired private TwoFactorSetupStore store;

    @Test
    @DisplayName("Should store, read and delete a pending secret")
    void shouldStoreReadAndDelete_whenPendingSecretIsManaged() {
        UUID userId = UUID.randomUUID();

        store.put(userId, "v1:iv:cipher", Duration.ofMinutes(1));

        assertThat(store.get(userId)).contains("v1:iv:cipher");
        store.delete(userId);
        assertThat(store.get(userId)).isEmpty();
    }
}
