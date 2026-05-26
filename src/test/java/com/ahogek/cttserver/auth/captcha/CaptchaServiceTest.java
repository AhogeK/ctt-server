package com.ahogek.cttserver.auth.captcha;

import com.ahogek.cttserver.common.config.properties.HcaptchaProperties;
import com.ahogek.cttserver.common.exception.BadGatewayException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaptchaService")
class CaptchaServiceTest {

    private static final String TEST_SITE_KEY = "10000000-ffff-ffff-ffff-000000000001";
    private static final String TEST_SECRET_KEY = "0x0000000000000000000000000000000000000000";
    private static final String TEST_VERIFY_URL = "https://api.hcaptcha.com/siteverify";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String TEST_TOKEN = "valid-captcha-token";

    @Mock private HcaptchaProperties captchaProperties;

    private CaptchaService captchaService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        lenient().when(captchaProperties.siteKey()).thenReturn(TEST_SITE_KEY);
        lenient().when(captchaProperties.secretKey()).thenReturn(TEST_SECRET_KEY);
        lenient().when(captchaProperties.verifyUrl()).thenReturn(TEST_VERIFY_URL);
        lenient().when(captchaProperties.timeout()).thenReturn(TEST_TIMEOUT);

        captchaService = new CaptchaService(captchaProperties);

        RestClient.Builder builder = RestClient.builder().baseUrl(TEST_VERIFY_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(captchaService, "restClient", builder.build());
    }

    @Nested
    @DisplayName("Successful Verification")
    class SuccessfulVerification {

        @Test
        @DisplayName("shouldVerifyCaptcha_whenTokenIsValid")
        void shouldVerifyCaptcha_whenTokenIsValid() {
            String responseJson =
                    """
                    {"success": true, "error-codes": []}""";

            mockServer
                    .expect(requestTo(TEST_VERIFY_URL))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            assertThatCode(() -> captchaService.verifyCaptcha(TEST_TOKEN))
                    .doesNotThrowAnyException();

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Verification Failure")
    class VerificationFailure {

        @Test
        @DisplayName("shouldThrowValidationException_whenCaptchaVerificationFails")
        void shouldThrowValidationException_whenCaptchaVerificationFails() {
            String responseJson =
                    """
                    {"success": false, "error-codes": ["invalid-input-response"]}""";

            mockServer
                    .expect(requestTo(TEST_VERIFY_URL))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> captchaService.verifyCaptcha(TEST_TOKEN))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.SECURITY_006);
                                assertThat(ex.getMessage()).contains("invalid-input-response");
                            });

            mockServer.verify();
        }

        @Test
        @DisplayName("shouldThrowValidationException_whenCaptchaApiReturnsNull")
        void shouldThrowValidationException_whenCaptchaApiReturnsNull() {
            mockServer
                    .expect(requestTo(TEST_VERIFY_URL))
                    .andRespond(withSuccess());

            assertThatThrownBy(() -> captchaService.verifyCaptcha(TEST_TOKEN))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.SECURITY_006);
                                assertThat(ex.getMessage()).contains("unknown-error");
                            });

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Network Errors")
    class NetworkErrors {

        @Test
        @DisplayName("shouldThrowBadGatewayException_whenNetworkTimeoutOccurs")
        void shouldThrowBadGatewayException_whenNetworkTimeoutOccurs() {
            mockServer
                    .expect(requestTo(TEST_VERIFY_URL))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(() -> captchaService.verifyCaptcha(TEST_TOKEN))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.SECURITY_007);
                                assertThat(ex.getMessage())
                                        .contains("Captcha service unreachable");
                            });

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Graceful Degradation")
    class GracefulDegradation {

        @Test
        @DisplayName("shouldSkipVerification_whenSiteKeyIsBlank")
        void shouldSkipVerification_whenSiteKeyIsBlank() {
            when(captchaProperties.siteKey()).thenReturn("");

            assertThatCode(() -> captchaService.verifyCaptcha(TEST_TOKEN))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("shouldThrowValidationException_whenTokenIsNull")
        void shouldThrowValidationException_whenTokenIsNull() {
            assertThatThrownBy(() -> captchaService.verifyCaptcha(null))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.SECURITY_006);
                                assertThat(ex.getMessage())
                                        .contains("Captcha token is required");
                            });
        }

        @Test
        @DisplayName("shouldThrowValidationException_whenTokenIsBlank")
        void shouldThrowValidationException_whenTokenIsBlank() {
            assertThatThrownBy(() -> captchaService.verifyCaptcha("   "))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.SECURITY_006);
                                assertThat(ex.getMessage())
                                        .contains("Captcha token is required");
                            });
        }
    }
}
