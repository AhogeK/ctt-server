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
                long lastPulledChangeId,
        @Schema(
                        description =
                                """
                                Replay escape hatch. The server advances the device watermark before \
                                it hands over a page, so a client that failed to APPLY a page would \
                                never see it again. Sending this field re-reads from that change id \
                                even though the watermark is further ahead; the watermark and the \
                                returned nextCursor still never move backwards, so a replay cannot \
                                rewind the device. Omit it for a normal pull.""",
                        example = "1200")
                @PositiveOrZero(message = "replayFromChangeId must be zero or positive")
                Long replayFromChangeId) {}
