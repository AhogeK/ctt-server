package com.ahogek.cttserver.device.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.dto.RegisterDeviceRequest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService Tests")
class DeviceServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final UUID DEVICE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    @Mock private DeviceRepository deviceRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private AuditLogService auditLogService;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService =
                new DeviceService(
                        deviceRepository,
                        refreshTokenRepository,
                        apiKeyRepository,
                        auditLogService);
    }

    private RegisterDeviceRequest request() {
        return new RegisterDeviceRequest(
                DEVICE_ID, "MacBook Pro", "macOS", "IntelliJ IDEA", "2026.1", "1.2.0");
    }

    private Device ownedDevice() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setUserId(USER_ID);
        return device;
    }

    private Device foreignDevice() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setUserId(OTHER_USER_ID);
        return device;
    }

    private ApiKey key() {
        ApiKey key = new ApiKey();
        key.setId(KEY_ID);
        return key;
    }

    @Nested
    @DisplayName("registerDevice")
    class RegisterDeviceTests {

        @Test
        @DisplayName("shouldCreateDevice_whenDeviceNotExists")
        void shouldCreateDevice_whenDeviceNotExists() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.empty());
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            DeviceResponse response = deviceService.registerDevice(USER_ID, null, request());

            // Then
            assertThat(response.id()).isEqualTo(DEVICE_ID);
            assertThat(response.deviceName()).isEqualTo("MacBook Pro");
            assertThat(response.platform()).isEqualTo("macOS");
            then(deviceRepository).should().save(any(Device.class));
            then(auditLogService)
                    .should()
                    .logSuccess(
                            USER_ID,
                            AuditAction.DEVICE_LINKED,
                            ResourceType.DEVICE,
                            DEVICE_ID.toString());
        }

        @Test
        @DisplayName("shouldUpdateMetadata_whenDeviceExistsAndOwned")
        void shouldUpdateMetadata_whenDeviceExistsAndOwned() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.of(ownedDevice()));

            // When
            deviceService.registerDevice(
                    USER_ID,
                    null,
                    new RegisterDeviceRequest(
                            DEVICE_ID, "Updated Name", "linux", "CLion", "2025.2", "1.3.0"));

            // Then
            ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
            then(deviceRepository).should().save(deviceCaptor.capture());
            Device saved = deviceCaptor.getValue();
            assertThat(saved.getId()).isEqualTo(DEVICE_ID);
            assertThat(saved.getDeviceName()).isEqualTo("Updated Name");
            assertThat(saved.getPlatform()).isEqualTo("linux");
            assertThat(saved.getIdeName()).isEqualTo("CLion");
            assertThat(saved.getIdeVersion()).isEqualTo("2025.2");
            assertThat(saved.getAppVersion()).isEqualTo("1.3.0");
            assertThat(saved.getLastSeenAt()).isNotNull();
            assertThat(saved.getRevokedAt()).isNull();
            then(auditLogService)
                    .should()
                    .logSuccess(
                            USER_ID,
                            AuditAction.DEVICE_LINKED,
                            ResourceType.DEVICE,
                            DEVICE_ID.toString());
        }

        @Test
        @DisplayName("shouldThrow409_whenDeviceOwnedByAnotherUser")
        void shouldThrow409_whenDeviceOwnedByAnotherUser() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.of(foreignDevice()));

            // When / Then
            RegisterDeviceRequest request = request();
            assertThatThrownBy(() -> deviceService.registerDevice(USER_ID, null, request))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(
                            e ->
                                    assertThat(((ConflictException) e).errorCode())
                                            .isEqualTo(ErrorCode.DEVICE_001));
            then(deviceRepository).should(never()).save(any(Device.class));
            then(auditLogService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("shouldBindApiKey_whenKeyIdProvided")
        void shouldBindApiKey_whenKeyIdProvided() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.empty());
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            ApiKey key = key();
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.of(key));

            // When
            deviceService.registerDevice(USER_ID, KEY_ID, request());

            // Then
            ArgumentCaptor<ApiKey> keyCaptor = ArgumentCaptor.forClass(ApiKey.class);
            then(apiKeyRepository).should().save(keyCaptor.capture());
            assertThat(keyCaptor.getValue().getDevice().getId()).isEqualTo(DEVICE_ID);
        }

        @Test
        @DisplayName("shouldNotBindApiKey_whenKeyIdNull")
        void shouldNotBindApiKey_whenKeyIdNull() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.empty());
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            deviceService.registerDevice(USER_ID, null, request());

            // Then
            then(apiKeyRepository).should(never()).findByIdAndUserId(any(), any());
            then(apiKeyRepository).should(never()).save(any(ApiKey.class));
        }

        @Test
        @DisplayName("shouldNotFailBinding_whenKeyNotFound")
        void shouldNotFailBinding_whenKeyNotFound() {
            // Given
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.empty());
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.empty());

            // When
            DeviceResponse response = deviceService.registerDevice(USER_ID, KEY_ID, request());

            // Then
            assertThat(response.id()).isEqualTo(DEVICE_ID);
            then(apiKeyRepository).should(never()).save(any(ApiKey.class));
        }

        @Test
        @DisplayName("shouldClearRevokedAt_whenDeviceReRegistered")
        void shouldClearRevokedAt_whenDeviceReRegistered() {
            // Given
            Device device = ownedDevice();
            device.setRevokedAt(Instant.now());
            given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.of(device));
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            deviceService.registerDevice(USER_ID, null, request());

            // Then
            ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
            then(deviceRepository).should().save(deviceCaptor.capture());
            assertThat(deviceCaptor.getValue().getRevokedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("revokeDevice")
    class RevokeDeviceTests {

        @Test
        @DisplayName("shouldSetRevokedAt_whenDeviceRevoked")
        void shouldSetRevokedAt_whenDeviceRevoked() {
            // Given
            Device device = ownedDevice();
            given(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID))
                    .willReturn(Optional.of(device));
            given(
                            refreshTokenRepository.revokeDeviceTokens(
                                    eq(USER_ID), eq(DEVICE_ID), any(Instant.class)))
                    .willReturn(1);
            given(deviceRepository.save(any(Device.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            deviceService.revokeDevice(USER_ID, DEVICE_ID);

            // Then
            ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
            then(deviceRepository).should().save(deviceCaptor.capture());
            assertThat(deviceCaptor.getValue().getRevokedAt()).isNotNull();
        }
    }
}
