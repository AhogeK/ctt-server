package com.ahogek.cttserver.probe;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Health check and readiness probe endpoints for local and test environments.
 *
 * <p>This controller provides endpoints for Kubernetes health checks and local testing. Not active
 * in production environments (restricted by {@code @Profile} annotation).
 *
 * @author Auto-generated
 * @since 0.1.0
 */
@Tag(
        name = "Probe Controller",
        description =
                "Health check and readiness probe endpoints for local and test environments. "
                        + "Provides endpoints for Kubernetes health checks, validation testing, and error handling verification. "
                        + "Only active in 'local' and 'test' profiles.")
@RestController
@RequestMapping("/probe")
@Profile({"local", "test"})
@Validated
public class ProbeController {

    public record EchoRequest(@NotBlank String name, @Min(1) int age) {}

    public record ValidRequest(@NotBlank @Size(max = 10) String value) {}

    @Operation(
            summary = "Health check",
            description =
                    "Returns a simple alive status. Used for Kubernetes liveness probes and basic health monitoring.")
    @ApiResponse(responseCode = "200", description = "Service is alive")
    @GetMapping("/ok")
    public ResponseEntity<RestApiResponse<Map<String, String>>> ok() {
        return ResponseEntity.ok(RestApiResponse.ok(Map.of("status", "alive")));
    }

    @Operation(
            summary = "Validate request body",
            description =
                    "Validates the request body using Jakarta Validation annotations. "
                            + "Returns 200 OK if valid, 400 Bad Request with validation errors if invalid.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Request body is valid"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003: Invalid request parameters")
            })
    @PostMapping("/validate")
    public ResponseEntity<RestApiResponse<Void>> validate(
            @Parameter(description = "Echo request with name and age fields", required = true)
                    @Valid
                    @RequestBody
                    EchoRequest req) {
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    @Operation(
            summary = "Trigger business error",
            description =
                    "Intentionally throws a NotFoundException (COMMON_002) for testing error handling. "
                            + "Returns 404 with standard error response.")
    @ApiResponse(
            responseCode = "404",
            description = "Not found - COMMON_002: Resource not found (intentional test error)")
    @GetMapping("/biz-error")
    public ResponseEntity<Void> bizError() {
        throw new NotFoundException(ErrorCode.COMMON_002);
    }

    @Operation(
            summary = "Trigger system error",
            description =
                    "Intentionally throws an InternalServerErrorException for testing error handling. "
                            + "Returns 500 with standard error response.")
    @ApiResponse(
            responseCode = "500",
            description =
                    "Internal server error - intentional test error for error handling verification")
    @GetMapping("/sys-error")
    public ResponseEntity<Void> sysError() {
        throw new InternalServerErrorException("intentional failure");
    }

    @Operation(
            summary = "Test missing parameter",
            description =
                    "Requires a mandatory 'param' query parameter. Returns 400 Bad Request if parameter is missing.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Parameter provided successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Missing required parameter - COMMON_003: Required parameter 'param' is missing")
            })
    @GetMapping("/missing-param")
    public ResponseEntity<RestApiResponse<Void>> missingParam(
            @Parameter(description = "Mandatory query parameter", required = true) @RequestParam
                    String param) {
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    @Operation(
            summary = "Test illegal argument",
            description =
                    "Requires a 'value' query parameter. Throws IllegalArgumentException if value is null. "
                            + "Returns 400 Bad Request.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Value provided successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Illegal argument - value cannot be null")
            })
    @GetMapping("/illegal-arg")
    public ResponseEntity<RestApiResponse<Void>> illegalArg(
            @Parameter(
                            description = "Optional query parameter (throws error if null)",
                            required = false)
                    @RequestParam(required = false)
                    String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    @Operation(
            summary = "Test constraint violation",
            description =
                    "Requires a 'value' parameter matching pattern ^[a-z]+$. "
                            + "Returns 400 Bad Request if pattern doesn't match.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Value matches pattern successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Constraint violation - value must match pattern ^[a-z]+$ (lowercase letters only)")
            })
    @GetMapping("/constraint-violation")
    public ResponseEntity<RestApiResponse<Void>> constraintViolation(
            @Parameter(
                            description =
                                    "Value must match pattern ^[a-z]+$ (lowercase letters only)",
                            required = true)
                    @RequestParam
                    @Pattern(regexp = "^[a-z]+$")
                    String value) {
        return ResponseEntity.ok(RestApiResponse.ok());
    }
}
