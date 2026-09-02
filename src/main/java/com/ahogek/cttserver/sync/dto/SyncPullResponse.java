package com.ahogek.cttserver.sync.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing changes since the client's last sync point")
public record SyncPullResponse(
        @Schema(
                        description =
                                "Changes to apply, in ascending change-id order; empty when up to date")
                List<SyncChangeDto> changes,
        @Schema(description = "Cursor to send on the next pull", example = "42") long nextCursor,
        @Schema(
                        description =
                                "True when more changes remain after this page; clients should pull"
                                        + " again with the returned nextCursor until false",
                        example = "false")
                boolean hasMore) {}
