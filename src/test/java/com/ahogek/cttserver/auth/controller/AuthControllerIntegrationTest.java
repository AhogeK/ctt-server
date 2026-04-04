package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired private MockMvcTester mvc;

    @Nested
    @DisplayName("POST /api/v1/auth/login - Security Behavior Tests")
    class SecurityBehaviorTests {

        @Test
        @DisplayName("Should return 400 when deviceId is blank (actual security, no @WithMockUser)")
        void shouldReturn400_whenBlankDeviceId_actualSecurity() {
            String request =
                    """
                {
                    "email": "test@example.com",
                    "password": "Test@1234",
                    "deviceId": ""
                }
                """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @DisplayName("Should accept request without X-Device-ID header (header is optional)")
        void shouldAcceptRequest_withoutDeviceIdHeader() {
            String request =
                    """
                {
                    "email": "test@example.com",
                    "password": "Test@1234",
                    "deviceId": "device-123"
                }
                """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .satisfies(
                            result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
    }
}
