package com.ahogek.cttserver.sync.dto;

import com.ahogek.cttserver.sync.enums.ChangeOp;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single change-log entry with the winning session snapshot for the client")
public record SyncChangeDto(
        @Schema(description = "Monotonically increasing change id", example = "42") long changeId,
        @Schema(
                        description = "Primary key of the affected coding session",
                        example = "9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f")
                UUID sessionId,
        @Schema(description = "Operation applied to the session", example = "UPSERT") ChangeOp op,
        @Schema(description = "Server version of the session after this change", example = "3")
                long serverVersion,
        @Schema(description = "When the change was recorded", example = "2026-08-25T10:30:00Z")
                Instant happenedAt,
        @Schema(description = "Project or repository name", example = "ctt-server")
                String projectName,
        @Schema(description = "Primary programming language", example = "Java") String language,
        @Schema(description = "Session start time", example = "2026-08-25T09:00:00Z")
                Instant startTime,
        @Schema(description = "Session end time", example = "2026-08-25T10:00:00Z") Instant endTime,
        @Schema(
                        description = "Last modification timestamp from the client",
                        example = "2026-08-25T10:00:00Z")
                Instant clientModifiedAt,
        @Schema(description = "Client-side version counter", example = "2") int clientVersion,
        @Schema(description = "Whether the session is soft-deleted", example = "false")
                boolean deleted) {}
