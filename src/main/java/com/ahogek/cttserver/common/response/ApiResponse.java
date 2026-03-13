package com.ahogek.cttserver.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Generic API response wrapper for consistent REST API responses.
 *
 * <p>This class provides a standardized envelope for all API responses,
 * ensuring consistent structure across all endpoints.</p>
 *
 * <p>Response format:</p>
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Instant timestamp;

    private ApiResponse(Builder<T> builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.data = builder.data;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null, "Operation successful");
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "Operation successful");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, T errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(errorDetails)
                .build();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }

    public T data() {
        return data;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public static final class Builder<T> {
        private boolean success = true;
        private String message;
        private T data;
        private Instant timestamp;

        private Builder() {}

        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(this);
        }
    }
}
