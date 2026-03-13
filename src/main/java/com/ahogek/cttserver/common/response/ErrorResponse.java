package com.ahogek.cttserver.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standardized error response for API errors.
 *
 * <p>This class provides detailed error information following RFC 7807
 * (Problem Details for HTTP APIs) specification.</p>
 *
 * <p>Response format:</p>
 * <pre>
 * {
 *   "code": "VALIDATION_ERROR",
 *   "message": "Invalid request parameters",
 *   "details": [
 *     {
 *       "field": "email",
 *       "message": "Invalid email format"
 *     }
 *   ],
 *   "traceId": "abc-123-def",
 *   "timestamp": "2026-03-14T03:23:12Z"
 * }
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14 03:23:12
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    private final String code;
    private final String message;
    private final List<FieldError> details;
    private final String traceId;
    private final Instant timestamp;

    private ErrorResponse(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.details = builder.details.isEmpty() ? null : new ArrayList<>(builder.details);
        this.traceId = builder.traceId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public static ErrorResponse of(String code, String message) {
        return builder()
                .code(code)
                .message(message)
                .build();
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return builder()
                .code(code)
                .message(message)
                .traceId(traceId)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public List<FieldError> details() {
        return details;
    }

    public String traceId() {
        return traceId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public record FieldError(String field, String message) {}

    public static final class Builder {
        private String code;
        private String message;
        private final List<FieldError> details = new ArrayList<>();
        private String traceId;
        private Instant timestamp;

        private Builder() {}

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder addDetail(String field, String message) {
            this.details.add(new FieldError(field, message));
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }
}
