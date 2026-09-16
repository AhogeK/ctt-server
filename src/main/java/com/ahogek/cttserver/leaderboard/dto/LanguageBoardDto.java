package com.ahogek.cttserver.leaderboard.dto;

import com.ahogek.cttserver.language.CanonicalLanguage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One language that has a leaderboard.
 *
 * @param name the canonical language name, to be passed back as the {@code language} parameter
 * @param type the category, so a client can group or filter the selector — a board list that mixed
 *     Java with Markdown without saying which is which would force the client to hardcode a
 *     preference
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-16
 */
@Schema(description = "A language that has a leaderboard")
public record LanguageBoardDto(
        @Schema(description = "Canonical language name", example = "Java") String name,
        @Schema(description = "Language category", example = "PROGRAMMING") String type) {

    /**
     * Builds the DTO from a canonical language.
     *
     * @param language the canonical language
     * @return the DTO
     */
    public static LanguageBoardDto from(CanonicalLanguage language) {
        return new LanguageBoardDto(language.name(), language.type().name());
    }
}
