package com.ahogek.cttserver.language;

import java.util.Objects;

/**
 * A coding-session language identifier after normalization.
 *
 * <p>Raw identifiers arrive from each IDE with its own spelling; this is the single form the rest
 * of the server groups, filters and reports by.
 *
 * @param name the canonical name, or the unchanged raw value when the vocabulary does not recognize
 *     it — an unrecognized value is preserved rather than coerced, so that it stays visible and can
 *     be classified later instead of being silently merged into a bucket nobody revisits
 * @param type the category the name belongs to
 * @param recognized whether the vocabulary knows this value, either as a language or as a known
 *     non-language; {@code false} marks a value that should be added to the vocabulary
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-16
 */
public record CanonicalLanguage(String name, LanguageType type, boolean recognized) {

    public CanonicalLanguage {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
