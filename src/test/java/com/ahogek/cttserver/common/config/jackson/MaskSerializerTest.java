package com.ahogek.cttserver.common.config.jackson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MaskSerializer}.
 *
 * @author AhogeK
 * @since 2026-03-16
 */
@DisplayName("MaskSerializer - DTO Field Desensitization")
class MaskSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should mask sensitive fields during JSON serialization")
    void shouldMaskSensitiveFields() throws Exception {
        UserResponse user = new UserResponse("user-123", "john@example.com", "secret-token-abc123");

        String json = objectMapper.writeValueAsString(user);

        assertThat(json)
                .contains("\"id\":\"user-123\"")
                .contains("\"email\":\"john@example.com\"")
                .contains("\"token\":\"******\"")
                .doesNotContain("secret-token-abc123");
    }

    @Test
    @DisplayName("Should mask password fields")
    void shouldMaskPasswordFields() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "my-secret-password");

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"email\":\"john@example.com\"")
                .contains("\"password\":\"******\"")
                .doesNotContain("my-secret-password");
    }

    @Test
    @DisplayName("Should handle null values by serializing as null")
    void shouldHandleNullValues() throws Exception {
        UserResponse user = new UserResponse("user-123", "john@example.com", null);

        String json = objectMapper.writeValueAsString(user);

        // Jackson does not invoke custom serializer for null values, outputs null directly
        assertThat(json).contains("\"token\":null");
    }

    @Test
    @DisplayName("Should handle empty string values")
    void shouldHandleEmptyStringValues() throws Exception {
        UserResponse user = new UserResponse("user-123", "john@example.com", "");

        String json = objectMapper.writeValueAsString(user);

        // Empty string is still a value, so serializer is invoked
        assertThat(json).contains("\"token\":\"******\"");
    }

    // Test DTOs
    private record UserResponse(
            String id, String email, @JsonSerialize(using = MaskSerializer.class) String token) {}

    private record LoginRequest(
            String email, @JsonSerialize(using = MaskSerializer.class) String password) {}
}
