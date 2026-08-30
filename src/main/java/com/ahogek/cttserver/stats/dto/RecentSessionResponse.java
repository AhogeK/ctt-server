package com.ahogek.cttserver.stats.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A recent coding session.
 *
 * @param sessionId server primary key
 * @param sessionUuid client-generated session UUID
 * @param projectName project or repository name
 * @param language primary programming language
 * @param startTime session start
 * @param endTime session end
 * @param durationSeconds raw session duration
 */
@Schema(description = "A recent coding session")
public record RecentSessionResponse(
        @Schema(
                        description = "Server primary key",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID sessionId,
        @Schema(
                        description = "Client-generated session UUID",
                        example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
                UUID sessionUuid,
        @Schema(description = "Project or repository name", example = "ctt-server")
                String projectName,
        @Schema(description = "Primary programming language", example = "Java") String language,
        @Schema(description = "Session start time", example = "2026-08-29T10:00:00Z")
                Instant startTime,
        @Schema(description = "Session end time", example = "2026-08-29T11:00:00Z") Instant endTime,
        @Schema(description = "Raw session duration in seconds", example = "3600")
                long durationSeconds) {}
