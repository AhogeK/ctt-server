package com.ahogek.cttserver.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Empty response for void API operations.
 *
 * <p>This class represents a successful operation that returns no content,
 * following HTTP 204 No Content semantics with a confirmation response.</p>
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EmptyResponse {

    private final boolean success;
    private final String message;
    private final Instant timestamp;

    private EmptyResponse(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public static EmptyResponse ok() {
        return ok("Operation successful");
    }

    public static EmptyResponse ok(String message) {
        return builder()
                .success(true)
                .message(message)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public static final class Builder {
        private boolean success = true;
        private String message;
        private Instant timestamp;

        private Builder() {}

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public EmptyResponse build() {
            return new EmptyResponse(this);
        }
    }
}
