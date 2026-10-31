package com.ahogek.cttserver.auth.twofactor.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Recovery codes returned exactly once.
 *
 * @param recoveryCodes ten single-use codes
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Recovery codes returned exactly once")
public record RecoveryCodesResponse(
        @Schema(
                        description = "Ten single-use recovery codes",
                        example = "[\"A1B2-C3D4-E5F6-G7H8\"]")
                List<String> recoveryCodes) {}
