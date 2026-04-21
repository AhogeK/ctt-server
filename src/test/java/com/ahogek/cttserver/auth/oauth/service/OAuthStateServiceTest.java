package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload;
import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload.Action;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthStateServiceTest {

    private static final String REDIRECT_URI = "https://app.example.com/oauth/callback";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock private StringRedisTemplate mockRedisTemplate;
    @Mock private ObjectMapper mockObjectMapper;
    @Mock private ValueOperations<String, String> mockValueOps;

    private OAuthStateService stateService;

    @BeforeEach
    void setUp() {
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        stateService = new OAuthStateService(mockRedisTemplate, mockObjectMapper);
    }

    @Nested
    @DisplayName("generateAndSaveState")
    class GenerateAndSaveState {

        @Test
        @DisplayName("should generate UUID state and store serialized payload in Redis with TTL")
        void shouldGenerateUuidAndStorePayloadInRedisWithTtl() throws JsonProcessingException {
            OAuthStatePayload payload = new OAuthStatePayload(Action.LOGIN, REDIRECT_URI, null);
            String serializedJson =
                    "{\"action\":\"LOGIN\",\"redirectUri\":\"https://app.example.com/oauth/callback\"}";

            when(mockObjectMapper.writeValueAsString(payload)).thenReturn(serializedJson);

            String stateId = stateService.generateAndSaveState(payload);

            assertThat(stateId).isNotBlank();

            String expectedKey = "oauth:state:" + stateId;
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            verify(mockValueOps)
                    .set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
            assertThat(valueCaptor.getValue()).isEqualTo(serializedJson);
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("should return different UUIDs on consecutive calls")
        void shouldReturnDifferentUuidsOnConsecutiveCalls() throws JsonProcessingException {
            OAuthStatePayload payload = new OAuthStatePayload(Action.LOGIN, REDIRECT_URI, null);
            when(mockObjectMapper.writeValueAsString(any())).thenReturn("{}");

            String stateId1 = stateService.generateAndSaveState(payload);
            String stateId2 = stateService.generateAndSaveState(payload);

            assertThat(stateId1).isNotEqualTo(stateId2);
        }

        @Test
        @DisplayName("should throw InternalServerErrorException when serialization fails")
        void shouldThrowInternalServerErrorException_whenSerializationFails()
                throws JsonProcessingException {
            OAuthStatePayload payload = new OAuthStatePayload(Action.LOGIN, REDIRECT_URI, null);
            when(mockObjectMapper.writeValueAsString(payload))
                    .thenThrow(new JsonProcessingException("broken") {});

            assertThatThrownBy(() -> stateService.generateAndSaveState(payload))
                    .isInstanceOf(InternalServerErrorException.class)
                    .hasMessageContaining("Failed to serialize OAuth state payload");

            verify(mockValueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should store BIND action payload with userId")
        void shouldStoreBindActionPayloadWithUserId() throws JsonProcessingException {
            OAuthStatePayload payload = new OAuthStatePayload(Action.BIND, REDIRECT_URI, USER_ID);
            when(mockObjectMapper.writeValueAsString(payload)).thenReturn("{}");

            String stateId = stateService.generateAndSaveState(payload);

            assertThat(stateId).isNotBlank();
            verify(mockValueOps).set("oauth:state:" + stateId, "{}", Duration.ofMinutes(10));
        }
    }

    @Nested
    @DisplayName("consumeState")
    class ConsumeState {

        @Test
        @DisplayName("should return payload and delete state on valid consumption")
        void shouldReturnPayloadAndDeleteState_onValidConsumption() throws JsonProcessingException {
            String stateId = "valid-uuid-123";
            String serializedJson =
                    "{\"action\":\"LOGIN\",\"redirectUri\":\"https://app.example.com/oauth/callback\"}";
            OAuthStatePayload expectedPayload =
                    new OAuthStatePayload(Action.LOGIN, REDIRECT_URI, null);

            when(mockValueOps.getAndDelete("oauth:state:" + stateId)).thenReturn(serializedJson);
            when(mockObjectMapper.readValue(serializedJson, OAuthStatePayload.class))
                    .thenReturn(expectedPayload);

            OAuthStatePayload result = stateService.consumeState(stateId);

            assertThat(result).isNotNull();
            assertThat(result.action()).isEqualTo(Action.LOGIN);
            assertThat(result.redirectUri()).isEqualTo(REDIRECT_URI);
            assertThat(result.userId()).isNull();

            verify(mockValueOps).getAndDelete("oauth:state:" + stateId);
        }

        @Test
        @DisplayName("should throw ForbiddenException when state does not exist")
        void shouldThrowForbiddenException_whenStateDoesNotExist() {
            String stateId = "nonexistent-uuid";

            when(mockValueOps.getAndDelete("oauth:state:" + stateId)).thenReturn(null);

            assertThatThrownBy(() -> stateService.consumeState(stateId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", com.ahogek.cttserver.common.exception.ErrorCode.AUTH_013);
        }

        @Test
        @DisplayName("should throw ForbiddenException on replay attack (second consume)")
        void shouldThrowForbiddenException_onReplayAttack() throws JsonProcessingException {
            String stateId = "replay-uuid";

            when(mockValueOps.getAndDelete("oauth:state:" + stateId))
                    .thenReturn("{\"action\":\"LOGIN\"}")
                    .thenReturn(null);
            when(mockObjectMapper.readValue(anyString(), eq(OAuthStatePayload.class)))
                    .thenReturn(new OAuthStatePayload(Action.LOGIN, REDIRECT_URI, null));

            OAuthStatePayload first = stateService.consumeState(stateId);
            assertThat(first.action()).isEqualTo(Action.LOGIN);

            assertThatThrownBy(() -> stateService.consumeState(stateId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", com.ahogek.cttserver.common.exception.ErrorCode.AUTH_013);
        }

        @Test
        @DisplayName("should throw ForbiddenException when state expired (TTL elapsed)")
        void shouldThrowForbiddenException_whenStateExpired() {
            String stateId = "expired-uuid";

            when(mockValueOps.getAndDelete("oauth:state:" + stateId)).thenReturn(null);

            assertThatThrownBy(() -> stateService.consumeState(stateId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", com.ahogek.cttserver.common.exception.ErrorCode.AUTH_013);
        }

        @Test
        @DisplayName("should throw InternalServerErrorException when deserialization fails")
        void shouldThrowInternalServerErrorException_whenDeserializationFails()
                throws JsonProcessingException {
            String stateId = "corrupt-uuid";
            String corruptJson = "{invalid json}";

            when(mockValueOps.getAndDelete("oauth:state:" + stateId)).thenReturn(corruptJson);
            when(mockObjectMapper.readValue(corruptJson, OAuthStatePayload.class))
                    .thenThrow(new JsonProcessingException("parse error") {});

            assertThatThrownBy(() -> stateService.consumeState(stateId))
                    .isInstanceOf(InternalServerErrorException.class)
                    .hasMessageContaining("Failed to deserialize OAuth state payload");
        }

        @Test
        @DisplayName("should return BIND payload with userId correctly")
        void shouldReturnBindPayloadWithUserId() throws JsonProcessingException {
            String stateId = "bind-uuid";
            String serializedJson =
                    "{\"action\":\"BIND\",\"redirectUri\":\"https://app.example.com/oauth/callback\",\"userId\":\"550e8400\"}";
            OAuthStatePayload expectedPayload =
                    new OAuthStatePayload(Action.BIND, REDIRECT_URI, USER_ID);

            when(mockValueOps.getAndDelete("oauth:state:" + stateId)).thenReturn(serializedJson);
            when(mockObjectMapper.readValue(serializedJson, OAuthStatePayload.class))
                    .thenReturn(expectedPayload);

            OAuthStatePayload result = stateService.consumeState(stateId);

            assertThat(result.action()).isEqualTo(Action.BIND);
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.redirectUri()).isEqualTo(REDIRECT_URI);
        }
    }
}
