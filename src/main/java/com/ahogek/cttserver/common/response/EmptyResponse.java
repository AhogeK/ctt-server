package com.ahogek.cttserver.common.response;

import java.time.Instant;

/**
 * Empty response for void API operations.
 *
 * <p>This class represents a successful operation that returns no content, following HTTP 204 No
 * Content semantics with a confirmation response.</p>
 *
 * <p>Response format:</p>
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
public record EmptyResponse(boolean success, String message, Instant timestamp) {

    public static EmptyResponse ok() {
        return ok("Operation successful");
    }

    public static EmptyResponse ok(String message) {
        return new EmptyResponse(true, message, Instant.now());
    }
}
