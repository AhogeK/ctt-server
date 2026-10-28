package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Session ids to revoke in one call.
 *
 * <p>Duplicates are ignored before the size limit is applied, so a client that concatenates pages
 * of a folder does not have to de-duplicate itself. The limit is checked after de-duplication: more
 * than 100 distinct ids is a validation error, not a partial revoke.
 *
 * @param sessionIds session identifiers as returned by the session list; at least one
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-28
 */
@Schema(description = "Login sessions to revoke in one call")
public record BatchRevokeSessionsRequest(
        @Schema(
                        description =
                                "Session identifiers, as returned by the session list. Duplicates"
                                        + " are ignored; at most 100 distinct ids per call",
                        example =
                                "[\"9f1c8f7a-2b4e-4d5a-9c3e-1a2b3c4d5e6f\","
                                        + " \"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d\"]")
                @NotEmpty(message = "sessionIds must not be empty")
                List<@NotNull(message = "sessionIds must not contain null") UUID> sessionIds) {}
