package com.ahogek.cttserver.auth.apikey.crypto;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JPA {@link AttributeConverter} for {@link ApiKeyScope} sets persisted as a JSONB column.
 *
 * <p>The {@code api_keys.scopes} column stores an ordered JSON array such as {@code ["READ",
 * "SYNC"]}. Use {@link EnumSet} on the entity to keep insertion order deterministic while still
 * providing {@code Set} semantics.
 *
 * <p>Setter injection is used so Hibernate 5+ can instantiate the converter while Spring still
 * supplies the shared {@link ObjectMapper}; this mirrors the pattern used by {@code
 * OAuthTokenConverter}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Component
@Converter(autoApply = false)
public class ApiKeyScopeConverter implements AttributeConverter<Set<ApiKeyScope>, String> {

    private static final TypeReference<Set<ApiKeyScope>> SCOPE_SET_TYPE = new TypeReference<>() {};

    private ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(Set<ApiKeyScope> attribute) {
        ensureObjectMapperInitialized();
        Set<ApiKeyScope> scopes = attribute == null ? EnumSet.noneOf(ApiKeyScope.class) : attribute;
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to serialize ApiKeyScope set to JSONB column", ex);
        }
    }

    @Override
    public Set<ApiKeyScope> convertToEntityAttribute(String dbData) {
        ensureObjectMapperInitialized();
        if (dbData == null || dbData.isBlank()) {
            return EnumSet.noneOf(ApiKeyScope.class);
        }
        try {
            Set<ApiKeyScope> raw = objectMapper.readValue(dbData, SCOPE_SET_TYPE);
            if (raw == null || raw.isEmpty()) {
                return EnumSet.noneOf(ApiKeyScope.class);
            }
            return EnumSet.copyOf(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialize ApiKeyScope set from JSONB column", ex);
        }
    }

    private void ensureObjectMapperInitialized() {
        if (objectMapper == null) {
            throw new IllegalStateException(
                    "ObjectMapper not initialized — Spring container must supply the dependency");
        }
    }
}
