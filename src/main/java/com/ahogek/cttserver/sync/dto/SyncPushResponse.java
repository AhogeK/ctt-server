package com.ahogek.cttserver.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response confirming the push was applied")
public record SyncPushResponse(
        @Schema(
                        description =
                                "Highest change id recorded after processing; use as next pull cursor",
                        example = "42")
                long nextCursor) {}
