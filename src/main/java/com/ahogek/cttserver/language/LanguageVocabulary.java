package com.ahogek.cttserver.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Normalizes raw IDE language identifiers to a single canonical vocabulary.
 *
 * <p>Each IDE reports languages in its own vocabulary — JetBrains a {@code FileType} name, VS Code
 * a {@code languageId} — so the same language arrives under different spellings ({@code JAVA} vs
 * {@code java}, {@code GitIgnore file} vs {@code ignore}). Anything that groups by language would
 * otherwise count one language several times.
 *
 * <p>The vocabulary is a resource rather than code because it is data: it grows as new IDEs and
 * file types appear, and reviewing a data file is easier than reviewing a Java table. It follows
 * GitHub Linguist for both names and categories, so the standard is maintained externally and this
 * server does not become the authority on what a language is called.
 *
 * <p>Two kinds of value are deliberately distinguished. A value the vocabulary knows to be a
 * non-language (an IDE internal such as {@code textmate}, an archive) resolves to {@link #OTHER}
 * and is merged with its peers. A value the vocabulary has never seen keeps its raw name and is
 * flagged as unrecognized, so it surfaces for classification instead of disappearing into {@code
 * Other} — the alternative would hide a genuine new language behind a bucket that nobody inspects.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-16
 */
@Component
public class LanguageVocabulary {

    private static final Logger log = LoggerFactory.getLogger(LanguageVocabulary.class);

    private static final String RESOURCE = "language/vocabulary.json";

    /** Canonical target for values the vocabulary recognizes as non-languages. */
    public static final CanonicalLanguage OTHER =
            new CanonicalLanguage("Other", LanguageType.OTHER, true);

    private final int version;

    private final Map<String, CanonicalLanguage> byToken;

    private final Set<String> nonLanguages;

    private final List<CanonicalLanguage> languages;

    @Autowired
    public LanguageVocabulary(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    LanguageVocabulary(VocabularyFile file) {
        this.version = file.version();

        Map<String, CanonicalLanguage> index = new HashMap<>();
        for (Map.Entry<String, String> entry : file.canonical().entrySet()) {
            index.putIfAbsent(
                    key(entry.getKey()),
                    new CanonicalLanguage(
                            entry.getKey(), LanguageType.parse(entry.getValue()), true));
        }
        for (Map.Entry<String, String> entry : file.aliases().entrySet()) {
            CanonicalLanguage target = index.get(key(entry.getValue()));
            if (target == null) {
                throw new IllegalStateException(
                        "Vocabulary alias '"
                                + entry.getKey()
                                + "' points at unknown language '"
                                + entry.getValue()
                                + "'");
            }
            index.putIfAbsent(key(entry.getKey()), target);
        }
        this.byToken = Map.copyOf(index);
        this.nonLanguages =
                Set.copyOf(file.nonLanguages().stream().map(LanguageVocabulary::key).toList());
        this.languages =
                index.values().stream()
                        .distinct()
                        .sorted(Comparator.comparing(CanonicalLanguage::name))
                        .toList();

        log.atInfo().log(
                "Language vocabulary v{} loaded: {} languages, {} aliases, {} non-language"
                        + " values",
                version,
                file.canonical().size(),
                file.aliases().size(),
                file.nonLanguages().size());
    }

    /**
     * Normalizes a raw identifier from any IDE.
     *
     * <p>Matching is case-insensitive and whitespace-insensitive because the IDEs disagree on
     * casing for no meaningful reason: {@code JAVA} and {@code Java} are one language, and folding
     * the case here is what makes them land in one bucket.
     *
     * @param raw the identifier as the originating IDE reported it
     * @return the canonical language; an unrecognized value is returned unchanged with {@code
     *     recognized} set to {@code false}
     */
    public CanonicalLanguage normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new CanonicalLanguage("", LanguageType.OTHER, false);
        }
        String token = key(raw);
        if (nonLanguages.contains(token)) {
            return OTHER;
        }
        CanonicalLanguage known = byToken.get(token);
        return known != null ? known : new CanonicalLanguage(raw, LanguageType.OTHER, false);
    }

    /**
     * Returns every canonical language, sorted by name.
     *
     * @return an immutable list for building option lists and filter endpoints
     */
    public List<CanonicalLanguage> languages() {
        return languages;
    }

    /**
     * Returns the vocabulary revision.
     *
     * @return the version recorded in the resource, for diagnostics
     */
    public int version() {
        return version;
    }

    private static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static VocabularyFile load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, VocabularyFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load " + RESOURCE, e);
        }
    }

    /**
     * The vocabulary resource's shape.
     *
     * @param version the vocabulary revision
     * @param canonical canonical name to Linguist category
     * @param aliases raw identifier to canonical name
     * @param nonLanguages raw identifiers that are not languages
     */
    public record VocabularyFile(
            int version,
            Map<String, String> canonical,
            Map<String, String> aliases,
            List<String> nonLanguages) {}
}
