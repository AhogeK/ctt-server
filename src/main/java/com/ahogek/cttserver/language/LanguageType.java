package com.ahogek.cttserver.language;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What kind of artifact a canonical language name denotes.
 *
 * <p>The categories mirror GitHub Linguist's {@code type} field. They exist so that consumers
 * answer "is this a programming language?" by lookup instead of by maintaining an exclusion list of
 * their own: a distribution cares about every kind because it reports where time went, while a "how
 * many languages do you code in" measure cares only about {@link #PROGRAMMING}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-16
 */
@Schema(description = "Category of a canonical language identifier")
public enum LanguageType {
    /** A language code is written in (Java, Kotlin, TypeScript). */
    PROGRAMMING,
    /** A markup or templating language (HTML, CSS, Markdown-adjacent formats). */
    MARKUP,
    /** A data or configuration format (JSON, YAML, SQL, TOML). */
    DATA,
    /** Prose written in a plain-text format (Markdown, reStructuredText). */
    PROSE,
    /**
     * Not a language in a user-meaningful sense: an IDE-internal pseudo-language ({@code textmate},
     * {@code ARCHIVE}) or a value the vocabulary does not recognize yet.
     */
    OTHER;

    /**
     * Parses a Linguist-style lowercase category name.
     *
     * @param value the category as written in the vocabulary resource
     * @return the matching constant, or {@link #OTHER} when the value is absent or unrecognized
     */
    static LanguageType parse(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "programming" -> PROGRAMMING;
            case "markup" -> MARKUP;
            case "data" -> DATA;
            case "prose" -> PROSE;
            default -> OTHER;
        };
    }
}
