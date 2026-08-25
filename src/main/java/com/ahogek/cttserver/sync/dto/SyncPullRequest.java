package com.ahogek.cttserver.sync.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to pull changes from the server since the last sync point")
public record SyncPullRequest(
        @Schema(
                        description = "Client device id that owns the sync cursor",
                        example = "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c")
                @NotNull(message = "deviceId is required")
                UUID deviceId,
        @Schema(
                        description =
                                "Last change id the client has applied; server resumes from here",
                        example = "42")
                @PositiveOrZero(message = "lastPulledChangeId must be zero or positive")
                long lastPulledChangeId) {}
