package com.ahogek.cttserver.common.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ClientIdentity}.
 *
 * @author AhogeK
 * @since 2026-03-17
 */
@DisplayName("ClientIdentity Tests")
class ClientIdentityTest {

    @Test
    @DisplayName("Should create identity with all fields")
    void shouldCreateIdentityWithAllFields() {
        UUID deviceId = UUID.randomUUID();
        ClientIdentity identity =
                new ClientIdentity(
                        deviceId, "Alice's MacBook", "macOS", "IntelliJ IDEA", "2024.1", "1.2.3");

        assertEquals(deviceId, identity.deviceId());
        assertEquals("Alice's MacBook", identity.deviceName());
        assertEquals("macOS", identity.platform());
        assertEquals("IntelliJ IDEA", identity.ideName());
        assertEquals("2024.1", identity.ideVersion());
        assertEquals("1.2.3", identity.appVersion());
    }

    @Test
    @DisplayName("Should identify plugin client when IDE name present")
    void shouldIdentifyPluginClient() {
        ClientIdentity pluginClient =
                new ClientIdentity(null, null, null, "IntelliJ IDEA", null, null);
        ClientIdentity webClient = new ClientIdentity(null, null, "Web", null, null, null);
        ClientIdentity emptyClient = ClientIdentity.empty();

        assertTrue(pluginClient.isPluginClient());
        assertFalse(webClient.isPluginClient());
        assertFalse(emptyClient.isPluginClient());
    }

    @Test
    @DisplayName("Should identify plugin client with blank IDE name as non-plugin")
    void shouldNotIdentifyBlankIdeAsPlugin() {
        ClientIdentity blankIde = new ClientIdentity(null, null, null, "   ", null, null);
        ClientIdentity emptyIde = new ClientIdentity(null, null, null, "", null, null);

        assertFalse(blankIde.isPluginClient());
        assertFalse(emptyIde.isPluginClient());
    }

    @Test
    @DisplayName("Should create empty identity")
    void shouldCreateEmptyIdentity() {
        ClientIdentity empty = ClientIdentity.empty();

        assertNull(empty.deviceId());
        assertNull(empty.deviceName());
        assertNull(empty.platform());
        assertNull(empty.ideName());
        assertNull(empty.ideVersion());
        assertNull(empty.appVersion());
    }

    @Test
    @DisplayName("Should handle null fields gracefully")
    void shouldHandleNullFields() {
        ClientIdentity identity = new ClientIdentity(null, null, null, null, null, null);

        assertNull(identity.deviceId());
        assertNull(identity.deviceName());
        assertFalse(identity.isPluginClient());
    }
}
