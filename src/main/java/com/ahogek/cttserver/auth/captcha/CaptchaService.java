package com.ahogek.cttserver.auth.captcha;

import com.ahogek.cttserver.common.config.properties.HcaptchaProperties;
import com.ahogek.cttserver.common.exception.BadGatewayException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Service for verifying hCaptcha tokens via the hCaptcha verification API.
 *
 * <p>Verifies captcha tokens submitted by users during registration or password reset to prevent
 * automated bot attacks. Gracefully degrades when hCaptcha is not configured (site-key is blank).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-23
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private final HcaptchaProperties captchaProperties;
    private final RestClient restClient;

    public CaptchaService(HcaptchaProperties captchaProperties) {
        this.captchaProperties = captchaProperties;
        int timeoutMs = (int) captchaProperties.timeout().toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient =
                RestClient.builder()
                        .baseUrl(captchaProperties.verifyUrl())
                        .requestFactory(requestFactory)
                        .build();
    }

    /**
     * Verifies an hCaptcha token against the hCaptcha verification API.
     *
     * <p>If hCaptcha is not configured (site-key is blank), verification is skipped with a warning
     * log. This allows the application to function without captcha in development environments.
     *
     * @param token the hCaptcha response token from the frontend widget
     * @throws ValidationException if the token is null/blank or verification fails
     * @throws BadGatewayException if the hCaptcha service is unreachable
     */
    public void verifyCaptcha(String token) {
        if (captchaProperties.siteKey() == null || captchaProperties.siteKey().isBlank()) {
            log.warn("hCaptcha not configured, skipping verification");
            return;
        }

        if (token == null || token.isBlank()) {
            throw new ValidationException(ErrorCode.SECURITY_006, "Captcha token is required");
        }

        try {
            MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
            formBody.add("response", token);
            formBody.add("secret", captchaProperties.secretKey());
            formBody.add("sitekey", captchaProperties.siteKey());

            CaptchaResponse response =
                    restClient
                            .post()
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(formBody)
                            .retrieve()
                            .body(CaptchaResponse.class);

            if (response == null || !response.success()) {
                List<String> errorCodes =
                        response != null ? response.errorCodes() : List.of("unknown-error");
                log.warn("Captcha verification failed: {}", errorCodes);
                throw new ValidationException(
                        ErrorCode.SECURITY_006, "Captcha verification failed: " + errorCodes);
            }
        } catch (Exception e) {
            if (e instanceof ValidationException) {
                throw e;
            }
            log.error("Failed to reach hCaptcha service: {}", e.getMessage());
            throw new BadGatewayException(ErrorCode.SECURITY_007, "Captcha service unreachable");
        }
    }

    /**
     * hCaptcha API response record.
     *
     * @param success whether the captcha verification succeeded
     * @param errorCodes list of error codes if verification failed
     */
    record CaptchaResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {}
}
