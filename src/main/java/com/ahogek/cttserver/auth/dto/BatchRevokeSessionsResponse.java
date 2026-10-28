package com.ahogek.cttserver.auth.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of a batch session revocation.
 *
 * <p>A batch is deliberately partial-success: ids that are unknown, already revoked, or owned by
 * another account are reported in {@link #skipped()} instead of failing the call. A retry after a
 * timeout therefore converges — the sessions revoked by the first attempt are simply skipped.
 *
 * @param revoked how many sessions this call actually revoked
 * @param skipped ids that were not revoked, sorted ascending
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-28
 */
@Schema(description = "Outcome of a batch session revocation")
public record BatchRevokeSessionsResponse(
        @Schema(description = "How many sessions this call revoked", example = "12") int revoked,
        @Schema(
                        description =
                                "Session ids that were already revoked, unknown, or owned by"
                                        + " another account",
                        example = "[\"9f1c8f7a-2b4e-4d5a-9c3e-1a2b3c4d5e6f\"]")
                List<UUID> skipped) {}
