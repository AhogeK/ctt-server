package com.ahogek.cttserver.common.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic API response wrapper for consistent REST API responses.
 *
 * <p>This class provides a standardized envelope for all API responses, ensuring consistent
 * structure across all endpoints.
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Operation successful",
 *   "data": { ... },
 *   "timestamp": "2026-03-14T03:23:12Z"
 * }
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@Schema(description = "Generic REST API response wrapper")
public record RestApiResponse<T>(
        @Schema(description = "Whether the operation succeeded", example = "true") boolean success,
        @Schema(description = "Response message", example = "Operation successful") String message,
        @Schema(description = "Response payload data") T data,
        @Schema(
                        description = "Response timestamp in ISO 8601 format",
                        example = "2026-03-14T03:23:12Z")
                Instant timestamp) {

    public static <T> RestApiResponse<T> ok() {
        return ok(null, "Operation successful");
    }

    public static <T> RestApiResponse<T> ok(T data) {
        return ok(data, "Operation successful");
    }

    public static <T> RestApiResponse<T> ok(T data, String message) {
        return new RestApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> RestApiResponse<T> error(String message) {
        return new RestApiResponse<>(false, message, null, Instant.now());
    }

    public static <T> RestApiResponse<T> error(String message, T errorDetails) {
        return new RestApiResponse<>(false, message, errorDetails, Instant.now());
    }
}
