package com.ahogek.cttserver.common.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Empty response for void API operations.
 *
 * <p>This class represents a successful operation that returns no content, following HTTP 204 No
 * Content semantics with a confirmation response.
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Resource deleted successfully",
 *   "timestamp": "2026-03-14T03:23:12Z"
 * }
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@Schema(description = "Empty response for void API operations")
public record EmptyResponse(
        @Schema(description = "Whether the operation succeeded", example = "true") boolean success,
        @Schema(description = "Confirmation message", example = "Operation successful")
                String message,
        @Schema(
                        description = "Response timestamp in ISO 8601 format",
                        example = "2026-03-14T03:23:12Z")
                Instant timestamp,
        @Schema(
                        description = "True if request was skipped due to idempotent window",
                        example = "false")
                Boolean idempotentSkip) {

    public static EmptyResponse ok() {
        return ok("Operation successful");
    }

    public static EmptyResponse ok(String message) {
        return new EmptyResponse(true, message, Instant.now(), null);
    }

    public static EmptyResponse ok(boolean idempotentSkip) {
        return new EmptyResponse(true, "Operation successful", Instant.now(), idempotentSkip);
    }

    public static EmptyResponse ok(String message, boolean idempotentSkip) {
        return new EmptyResponse(true, message, Instant.now(), idempotentSkip);
    }
}
