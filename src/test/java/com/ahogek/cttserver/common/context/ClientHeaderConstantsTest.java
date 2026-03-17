package com.ahogek.cttserver.common.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ClientHeaderConstants}.
 *
 * @author AhogeK
 * @since 2026-03-17
 */
@DisplayName("ClientHeaderConstants Tests")
class ClientHeaderConstantsTest {

    @Test
    @DisplayName("Should have correct header constant values")
    void shouldHaveCorrectHeaderConstantValues() {
        assertEquals("X-Device-ID", ClientHeaderConstants.HEADER_DEVICE_ID);
        assertEquals("X-Device-Name", ClientHeaderConstants.HEADER_DEVICE_NAME);
        assertEquals("X-Platform", ClientHeaderConstants.HEADER_PLATFORM);
        assertEquals("X-IDE-Name", ClientHeaderConstants.HEADER_IDE_NAME);
        assertEquals("X-IDE-Version", ClientHeaderConstants.HEADER_IDE_VERSION);
        assertEquals("X-App-Version", ClientHeaderConstants.HEADER_APP_VERSION);
    }

    @Test
    @DisplayName("Should have private constructor to prevent instantiation")
    void shouldHavePrivateConstructor() throws NoSuchMethodException {
        Constructor<ClientHeaderConstants> constructor =
                ClientHeaderConstants.class.getDeclaredConstructor();
        assertTrue(
                java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
                "Constructor should be private");
    }
}
