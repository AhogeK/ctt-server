package com.ahogek.cttserver.auth.oauth.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * OAuth provider enumeration for third-party authentication integration.
 *
 * @author AhogeK
 * @since 2026-04-12
 */
public enum OAuthProvider {

    /** GitHub OAuth provider. */
    GITHUB("github");

    private final String value;

    OAuthProvider(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OAuthProvider fromValue(String value) {
        return Arrays.stream(values())
                .filter(p -> p.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown OAuth provider: " + value));
    }
}
