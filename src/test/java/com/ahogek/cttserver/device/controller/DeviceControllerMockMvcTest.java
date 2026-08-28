package com.ahogek.cttserver.device.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.service.DeviceService;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * {@link DeviceController} MockMvc tests.
 *
 * <p>Covers device registration (POST), listing (GET) and revocation (DELETE) with the controller
 * slice: happy paths, validation, ownership-conflict mapping and missing-authentication rejection.
 */
@BaseControllerSliceTest(
        value = DeviceController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("DeviceController MockMvc Tests")
class DeviceControllerMockMvcTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID DEVICE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @Autowired private MockMvcTester mvc;

    @MockitoBean private DeviceService deviceService;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    private CurrentUser currentUser() {
        return new CurrentUser(
                USER_ID,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("ROLE_USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private DeviceResponse stubDevice() {
        return new DeviceResponse(
                DEVICE_ID,
                "MacBook Pro",
                "macOS",
                "IntelliJ IDEA",
                "2026.1",
                "1.2.0",
                Instant.parse("2026-08-28T10:00:00Z"),
                Instant.parse("2026-08-28T10:00:00Z"));
    }

    @Nested
    @DisplayName("POST /api/v1/devices")
    class RegisterDeviceTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with registered device on success")
        void shouldReturn200_whenRegistered() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            deviceService.registerDevice(
                                    BDDMockito.eq(USER_ID), BDDMockito.isNull(), BDDMockito.any()))
                    .willReturn(stubDevice());

            String body =
                    """
                    {
                      "deviceId": "%s",
                      "deviceName": "MacBook Pro",
                      "platform": "macOS",
                      "ideName": "IntelliJ IDEA",
                      "ideVersion": "2026.1",
                      "appVersion": "1.2.0"
                    }
                    """
                            .formatted(DEVICE_ID);

            var result =
                    mvc.post()
                            .uri("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf())
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.id")
                    .isEqualTo(DEVICE_ID.toString());
            BDDMockito.then(deviceService)
                    .should()
                    .registerDevice(BDDMockito.eq(USER_ID), BDDMockito.isNull(), BDDMockito.any());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 COMMON_003 when deviceId is missing")
        void shouldReturn400_whenDeviceIdMissing() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());

            String body =
                    """
                    {
                      "deviceName": "MacBook Pro"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 409 DEVICE_001 when device is owned by another user")
        void shouldReturn409_whenDeviceOwnedByAnotherUser() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            deviceService.registerDevice(
                                    BDDMockito.eq(USER_ID), BDDMockito.isNull(), BDDMockito.any()))
                    .willThrow(new ConflictException(ErrorCode.DEVICE_001));

            String body =
                    """
                    {"deviceId": "%s"}
                    """
                            .formatted(DEVICE_ID);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("DEVICE_001");
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            String body =
                    """
                    {"deviceId": "%s"}
                    """
                            .formatted(DEVICE_ID);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/devices")
    class ListDeviceTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with the user's devices")
        void shouldReturn200_whenDevicesListed() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(deviceService.listUserDevices(USER_ID))
                    .willReturn(List.of(stubDevice()));

            var result = mvc.get().uri("/api/v1/devices").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data[0].id")
                    .isEqualTo(DEVICE_ID.toString());
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/devices").exchange()).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/devices/{deviceId}")
    class RevokeDeviceTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 on successful revocation")
        void shouldReturn200_whenRevoked() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());

            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/devices/{deviceId}", DEVICE_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatusOk();

            BDDMockito.then(deviceService).should().revokeDevice(USER_ID, DEVICE_ID);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/devices/{deviceId}", DEVICE_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401);
        }
    }
}
