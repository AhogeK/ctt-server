package com.ahogek.cttserver.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Email status information")
public record EmailStatusResponse(
        @Schema(description = "Current email address", example = "user@example.com") String email,
        @Schema(description = "Whether email is verified", example = "true") boolean emailVerified,
        @Schema(description = "Whether an email change request is pending", example = "false")
                boolean emailChangePending,
        @Schema(
                        description = "The new email address pending verification",
                        example = "new@example.com",
                        nullable = true)
                @JsonInclude(JsonInclude.Include.ALWAYS)
                String pendingNewEmail) {}
