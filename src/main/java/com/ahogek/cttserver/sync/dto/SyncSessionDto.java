package com.ahogek.cttserver.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A coding session state submitted by the client for push.
 *
 * <p>The two string fields carry a length bound matching the column they are stored in. Without it
 * an oversized value passes validation and fails inside the batched insert, and because the batch
 * is one multi-row statement the whole push is rejected rather than the offending session — the
 * client sees a server error it cannot act on, and retrying reproduces it.
 *
 * <p>{@code ideName}/{@code ideVersion} are optional and describe the IDE that produced the
 * session, not the device that pushed it: a device id is installation-scoped, so every IDE on one
 * machine shares it. Clients older than this field send neither, and their sessions stay null —
 * readers fall back to the origin device's registration.
 */
@Schema(description = "A coding session state submitted by the client for push")
public record SyncSessionDto(
        @Schema(
                        description = "Client-generated session UUID, unique per user",
                        example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
                @NotNull(message = "sessionUuid is required")
                UUID sessionUuid,
        @Schema(description = "Project or repository name", example = "ctt-server")
                @NotBlank(message = "projectName is required")
                @Size(max = 255, message = "projectName must not exceed 255 characters")
                String projectName,
        @Schema(description = "Primary programming language", example = "Java")
                @NotBlank(message = "language is required")
                @Size(max = 50, message = "language must not exceed 50 characters")
                String language,
        @Schema(description = "Session start time", example = "2026-08-25T09:00:00Z")
                @NotNull(message = "startTime is required")
                Instant startTime,
        @Schema(description = "Session end time", example = "2026-08-25T10:00:00Z")
                @NotNull(message = "endTime is required")
                Instant endTime,
        @Schema(
                        description = "Last modification timestamp from the client",
                        example = "2026-08-25T10:00:00Z")
                @NotNull(message = "clientModifiedAt is required")
                Instant clientModifiedAt,
        @Schema(description = "Client-side version counter for LWW resolution", example = "2")
                @PositiveOrZero(message = "clientVersion must be zero or positive")
                int clientVersion,
        @Schema(
                        description =
                                "IDE that produced this session; null for clients that do not report it",
                        example = "IntelliJ IDEA",
                        nullable = true)
                @Size(max = 100, message = "ideName must not exceed 100 characters")
                String ideName,
        @Schema(
                        description = "IDE build version that produced this session",
                        example = "2026.1",
                        nullable = true)
                @Size(max = 50, message = "ideVersion must not exceed 50 characters")
                String ideVersion,
        @Schema(description = "Whether the client deleted this session", example = "false")
                boolean deleted) {}
