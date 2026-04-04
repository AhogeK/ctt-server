package com.ahogek.cttserver.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI configuration for JWT Bearer authentication.
 *
 * <p>Configures Swagger UI with HTTP Bearer JWT security scheme. Protected endpoints must
 * explicitly declare {@code @SecurityRequirement(name = "bearerAuth")} to display lock icon. Public
 * endpoints (with {@code @PublicApi}) will not show lock icon.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-04
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "JWT Bearer token authentication. "
                                                                + "Format: Authorization: Bearer <token>")));
    }
}
