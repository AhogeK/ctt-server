package com.ahogek.cttserver.leaderboard.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The languages a client can request a leaderboard for.
 *
 * @param languages the boards, ordered by name
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-16
 */
@Schema(description = "Languages that have a leaderboard")
public record LanguageBoardsResponse(
        @Schema(description = "Available language boards") List<LanguageBoardDto> languages) {}
