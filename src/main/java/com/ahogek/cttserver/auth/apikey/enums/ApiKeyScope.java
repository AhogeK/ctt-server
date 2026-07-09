package com.ahogek.cttserver.auth.apikey.enums;

/**
 * Authorization scopes granted to an API key.
 *
 * <p>Each scope is mapped to a Spring Security authority string (e.g. {@code ROLE_API_KEY_READ}) so
 * that the scope can be enforced declaratively via {@code @PreAuthorize}. Authorities follow the
 * {@code ROLE_API_KEY_<SCOPE>} convention to clearly distinguish API-key-based authorization from
 * role-based grants assigned to web users.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
public enum ApiKeyScope {

    /** Read-only access (e.g. profile, statistics queries). */
    READ("ROLE_API_KEY_READ"),

    /** Mutating access on user-owned resources that are not part of the sync engine. */
    WRITE("ROLE_API_KEY_WRITE"),

    /** Authorization to call the bidirectional sync engine endpoints. */
    SYNC("ROLE_API_KEY_SYNC"),

    /** Full administrative access; supersedes all other scopes. */
    ADMIN("ROLE_API_KEY_ADMIN");

    private final String authority;

    ApiKeyScope(String authority) {
        this.authority = authority;
    }

    /**
     * Returns the Spring Security authority string for this scope.
     *
     * @return authority in the form {@code ROLE_API_KEY_<SCOPE>}
     */
    public String getAuthority() {
        return authority;
    }
}
