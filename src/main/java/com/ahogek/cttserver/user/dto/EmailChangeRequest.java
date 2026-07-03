package com.ahogek.cttserver.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to change email address")
public record EmailChangeRequest(
        @Schema(description = "New email address", example = "newemail@example.com")
                @NotBlank(message = "Email is required")
                @Email(message = "Invalid email format")
                @Size(max = 255, message = "Email must not exceed 255 characters")
                String newEmail,
        @Schema(
                        description =
                                "Current password for verification (required if user has password)",
                        example = "CurrentPass123!")
                String password) {}
