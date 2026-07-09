package com.ahogek.cttserver.auth.apikey.crypto;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyScopeConverter Tests")
class ApiKeyScopeConverterTest {

    private ApiKeyScopeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ApiKeyScopeConverter();
        converter.setObjectMapper(new ObjectMapper());
    }

    @Test
    @DisplayName("shouldRoundTrip_singleScope")
    void shouldRoundTrip_singleScope() {
        Set<ApiKeyScope> original = EnumSet.of(ApiKeyScope.READ);
        String json = converter.convertToDatabaseColumn(original);
        Set<ApiKeyScope> result = converter.convertToEntityAttribute(json);
        assertThat(result).containsExactly(ApiKeyScope.READ);
    }

    @Test
    @DisplayName("shouldRoundTrip_multipleScopes")
    void shouldRoundTrip_multipleScopes() {
        Set<ApiKeyScope> original =
                EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC, ApiKeyScope.ADMIN);
        String json = converter.convertToDatabaseColumn(original);
        Set<ApiKeyScope> result = converter.convertToEntityAttribute(json);
        assertThat(result).containsExactly(ApiKeyScope.READ, ApiKeyScope.SYNC, ApiKeyScope.ADMIN);
    }

    @Test
    @DisplayName("shouldReturnEmptyEnumSet_whenJsonArrayIsEmpty")
    void shouldReturnEmptyEnumSet_whenJsonArrayIsEmpty() {
        Set<ApiKeyScope> result = converter.convertToEntityAttribute("[]");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shouldReturnEmptyEnumSet_whenJsonArrayIsNull")
    void shouldReturnEmptyEnumSet_whenJsonArrayIsNull() {
        Set<ApiKeyScope> result = converter.convertToEntityAttribute(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shouldReturnEmptyEnumSet_whenJsonArrayIsBlank")
    void shouldReturnEmptyEnumSet_whenJsonArrayIsBlank() {
        Set<ApiKeyScope> result = converter.convertToEntityAttribute("  ");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shouldSerializeEmptySet_toEmptyJsonArray")
    void shouldSerializeEmptySet_toEmptyJsonArray() {
        String json = converter.convertToDatabaseColumn(EnumSet.noneOf(ApiKeyScope.class));
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("shouldSerializeNull_toEmptyJsonArray")
    void shouldSerializeNull_toEmptyJsonArray() {
        String json = converter.convertToDatabaseColumn(null);
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("shouldThrowIllegalStateException_whenObjectMapperNotInitialized")
    void shouldThrowIllegalStateException_whenObjectMapperNotInitialized() {
        ApiKeyScopeConverter uninitialized = new ApiKeyScopeConverter();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> uninitialized.convertToDatabaseColumn(EnumSet.of(ApiKeyScope.READ)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ObjectMapper not initialized");
    }
}
