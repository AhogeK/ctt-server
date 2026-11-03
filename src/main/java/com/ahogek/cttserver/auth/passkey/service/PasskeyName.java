package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

/**
 * Normalizes and bounds passkey labels for both registration and rename.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
final class PasskeyName {

    private static final int MAX_LENGTH = 64;
    private static final String DEFAULT_NAME = "Passkey";

    private PasskeyName() {}

    /**
     * Normalizes a registration label, defaulting a blank value.
     *
     * @param name the optional submitted label
     * @return the stored label
     */
    static String forRegistration(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_NAME;
        }
        return bounded(name.trim());
    }

    /**
     * Normalizes a required rename label.
     *
     * @param name the submitted label
     * @return the stored label
     */
    static String forRename(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.COMMON_003, "Passkey name is required");
        }
        return bounded(name.trim());
    }

    private static String bounded(String name) {
        if (name.length() > MAX_LENGTH) {
            throw new ValidationException(ErrorCode.COMMON_003, "Passkey name is too long");
        }
        return name;
    }
}
