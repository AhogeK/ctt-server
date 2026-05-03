package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public configuration REST controller.
 *
 * <p>Exposes non-sensitive application configuration to clients without authentication.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-03
 */
@Tag(name = "Configuration", description = "Public configuration endpoints")
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final TermsProperties termsProperties;

    public ConfigController(TermsProperties termsProperties) {
        this.termsProperties = termsProperties;
    }

    @Operation(
            summary = "Get public configuration",
            description = "Returns public application configuration including the current terms of service version")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Public configuration returned successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class)))
            })
    @PublicApi(reason = "Public config endpoint - no auth required")
    @GetMapping("/public")
    public ResponseEntity<RestApiResponse<PublicConfigResponse>> getPublicConfig() {
        return ResponseEntity.ok(
                RestApiResponse.ok(new PublicConfigResponse(termsProperties.currentVersion())));
    }

    @Schema(description = "Public application configuration")
    public record PublicConfigResponse(
            @Schema(description = "Current active terms of service version", example = "1.0.0")
                    String termsVersion) {}
}
