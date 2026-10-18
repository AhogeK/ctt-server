package com.ahogek.cttserver.user.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for changing the current user's profile.
 *
 * <p>The display name follows the same rule as registration — the shared {@code
 * REGEX_DISPLAY_NAME}, which admits no whitespace, so no trimming happens here.
 *
 * @param displayName the new display name
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-18
 */
@Schema(description = "Profile update request")
public record UpdateProfileRequest(
        @Schema(description = "New display name", example = "AhogeK")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Pattern(
                        regexp = ValidationConstants.REGEX_DISPLAY_NAME,
                        message = ValidationConstants.MSG_NAME_INVALID)
                String displayName) {}
