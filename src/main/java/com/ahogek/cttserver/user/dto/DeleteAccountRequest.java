package com.ahogek.cttserver.user.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirmation for deleting one's own account.
 *
 * <p>Deletion is irreversible from the caller's side, so it is confirmed the way the other
 * irreversible action is: by proving the password rather than only presenting a session token,
 * matching {@code ChangePasswordRequest}. Accounts created through an OAuth provider have no
 * password to prove, and for them the session is the strongest credential that exists — which is
 * also how {@code SetPasswordRequest} treats them.
 *
 * @param password the account password; required for accounts that have one
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-17
 */
@Schema(description = "Confirmation for deleting one's own account")
public record DeleteAccountRequest(
        @Schema(
                        description =
                                "Account password. Required for accounts that have one; ignored for"
                                        + " accounts created through an OAuth provider, which have"
                                        + " none",
                        example = "StrongPass123!")
                @Size(max = 100, message = "password must not exceed 100 characters")
                String password) {}
