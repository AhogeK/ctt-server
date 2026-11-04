package com.ahogek.cttserver.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to push local session changes to the server")
public record SyncPushRequest(
        @Schema(
                        description = "Client device id that originated the changes",
                        example = "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c")
                @NotNull(message = "deviceId is required")
                UUID deviceId,
        @Schema(
                        description =
                                "Session states to push; processed atomically as one batch, at most "
                                        + "1000 entries")
                @NotEmpty(message = "sessions must not be empty")
                @Size(max = 1000, message = "sessions must contain at most 1000 entries")
                @Valid
                List<SyncSessionDto> sessions) {}
