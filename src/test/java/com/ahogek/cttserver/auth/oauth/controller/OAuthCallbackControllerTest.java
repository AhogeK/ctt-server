package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.common.ratelimit.RateLimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuthCallbackController Annotation Tests")
class OAuthCallbackControllerTest {

    @Nested
    @DisplayName("Endpoint Annotations")
    class EndpointAnnotations {

        @Test
        @DisplayName("authorize endpoint should have @PublicApi")
        void authorizeEndpoint_shouldHavePublicApi() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod("authorize", OAuthProvider.class);
            assertThat(
                            method.getAnnotation(
                                    com.ahogek.cttserver.common.security.annotation.PublicApi
                                            .class))
                    .isNotNull();
        }

        @Test
        @DisplayName("callback endpoint should have @PublicApi")
        void callbackEndpoint_shouldHavePublicApi() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod(
                            "callback",
                            OAuthProvider.class,
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            jakarta.servlet.http.HttpServletResponse.class);
            assertThat(
                            method.getAnnotation(
                                    com.ahogek.cttserver.common.security.annotation.PublicApi
                                            .class))
                    .isNotNull();
        }

        @Test
        @DisplayName("authorize endpoint should have @RateLimit(30/hour)")
        void authorizeEndpoint_shouldHaveRateLimit() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod("authorize", OAuthProvider.class);
            RateLimit rateLimit = method.getAnnotation(RateLimit.class);
            assertThat(rateLimit).isNotNull();
            assertThat(rateLimit.limit()).isEqualTo(30);
            assertThat(rateLimit.windowSeconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("callback endpoint should have @RateLimit(60/hour)")
        void callbackEndpoint_shouldHaveRateLimit() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod(
                            "callback",
                            OAuthProvider.class,
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            jakarta.servlet.http.HttpServletResponse.class);
            RateLimit rateLimit = method.getAnnotation(RateLimit.class);
            assertThat(rateLimit).isNotNull();
            assertThat(rateLimit.limit()).isEqualTo(60);
            assertThat(rateLimit.windowSeconds()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("Swagger Documentation")
    class SwaggerDocumentation {

        @Test
        @DisplayName("authorize endpoint should have @Operation")
        void authorizeEndpoint_shouldHaveOperation() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod("authorize", OAuthProvider.class);

            Operation operation = method.getAnnotation(Operation.class);
            assertThat(operation).isNotNull();
            assertThat(operation.summary()).isEqualTo("Initiate OAuth authorization");
            assertThat(operation.description()).contains("CSRF-protected state");
        }

        @Test
        @DisplayName("authorize endpoint should have @ApiResponses")
        void authorizeEndpoint_shouldHaveApiResponses() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod("authorize", OAuthProvider.class);

            ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
            assertThat(apiResponses).isNotNull();
            assertThat(apiResponses.value()).hasSize(2);

            ApiResponse okResponse = apiResponses.value()[0];
            assertThat(okResponse.responseCode()).isEqualTo("200");
            assertThat(okResponse.description()).contains("Authorization URL generated");
        }

        @Test
        @DisplayName("callback endpoint should have @Operation")
        void callbackEndpoint_shouldHaveOperation() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod(
                            "callback",
                            OAuthProvider.class,
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            jakarta.servlet.http.HttpServletResponse.class);

            Operation operation = method.getAnnotation(Operation.class);
            assertThat(operation).isNotNull();
            assertThat(operation.summary()).isEqualTo("Handle OAuth callback");
            assertThat(operation.description()).contains("validates state");
        }

        @Test
        @DisplayName("callback endpoint should have @ApiResponses")
        void callbackEndpoint_shouldHaveApiResponses() throws NoSuchMethodException {
            Method method =
                    OAuthCallbackController.class.getMethod(
                            "callback",
                            OAuthProvider.class,
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            jakarta.servlet.http.HttpServletResponse.class);

            ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
            assertThat(apiResponses).isNotNull();
            assertThat(apiResponses.value()).hasSize(2);

            ApiResponse redirectResponse = apiResponses.value()[0];
            assertThat(redirectResponse.responseCode()).isEqualTo("302");
            assertThat(redirectResponse.description()).contains("Redirect to frontend");
        }

        @Test
        @DisplayName("Controller should have @Tag annotation")
        void controller_shouldHaveTagAnnotation() {
            io.swagger.v3.oas.annotations.tags.Tag tag =
                    OAuthCallbackController.class.getAnnotation(
                            io.swagger.v3.oas.annotations.tags.Tag.class);
            assertThat(tag).isNotNull();
            assertThat(tag.name()).isEqualTo("OAuth");
            assertThat(tag.description()).contains("GitHub OAuth authentication");
        }
    }
}
