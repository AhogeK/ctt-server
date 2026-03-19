package com.ahogek.cttserver.common.exception;

/**
 * Exception for internal server errors that indicate system failures.
 *
 * <p>Used for unexpected errors that require full stack trace logging and critical alerts. Unlike
 * {@link BusinessException} subclasses, this exception triggers ERROR level logging with full stack
 * traces for debugging production issues.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class InternalServerErrorException extends RuntimeException {

    /**
     * Creates a new internal server error exception with the specified message.
     *
     * @param message the error message
     */
    public InternalServerErrorException(String message) {
        super(message);
    }

    /**
     * Creates a new internal server error exception with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause of the error
     */
    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
