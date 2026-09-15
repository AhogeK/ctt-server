package com.ahogek.cttserver.language;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Language vocabulary")
class LanguageVocabularyTest {

    private static final LanguageVocabulary VOCABULARY = new LanguageVocabulary(new ObjectMapper());

    /**
     * Every language value the JetBrains plugin has actually recorded, measured from a real session
     * store. Covers the values a user's own statistics are built from, which the file-type list
     * cannot: values like JAVA are registered programmatically and never appear in a plugin
     * manifest.
     */
    private static final List<String> OBSERVED_JETBRAINS_VALUES =
            List.of(
                    "Kotlin",
                    "JAVA",
                    "JavaScript",
                    "Markdown",
                    "HTML",
                    "TOML",
                    "CSS",
                    "XML",
                    "SVG",
                    "Properties",
                    "textmate",
                    "GitIgnore file",
                    "TypeScript",
                    "SQL");

    @Nested
    @DisplayName("normalization")
    class NormalizationTests {

        @Test
        @DisplayName("should fold case so the same language is one bucket")
        void shouldFoldCase_whenIdesDisagreeOnCasing() {
            assertThat(VOCABULARY.normalize("JAVA").name())
                    .isEqualTo(VOCABULARY.normalize("java").name())
                    .isEqualTo("Java");
            assertThat(VOCABULARY.normalize("TypeScript").name())
                    .isEqualTo(VOCABULARY.normalize("typescript").name())
                    .isEqualTo("TypeScript");
        }

        @Test
        @DisplayName("should converge the two IDEs' spellings on one canonical name")
        void shouldConverge_whenSpellingsDifferAcrossIdes() {
            // JetBrains "Kotlin" vs a lowercase VS Code languageId; VS Code "shellscript" vs Shell.
            assertThat(VOCABULARY.normalize("Kotlin").name())
                    .isEqualTo(VOCABULARY.normalize("kotlin").name());
            assertThat(VOCABULARY.normalize("shellscript").name()).isEqualTo("Shell");
            assertThat(VOCABULARY.normalize("jade").name()).isEqualTo("Pug");
            assertThat(VOCABULARY.normalize("cuda-cpp").name()).isEqualTo("Cuda");
        }

        @Test
        @DisplayName("should rename values whose IDE name differs from the canonical one")
        void shouldApplyAlias_whenIDENameDiffers() {
            assertThat(VOCABULARY.normalize("GitIgnore file").name()).isEqualTo("Ignore List");
            assertThat(VOCABULARY.normalize("Properties").name()).isEqualTo("Java Properties");
            assertThat(VOCABULARY.normalize("C/C++").name()).isEqualTo("C++");
            assertThat(VOCABULARY.normalize("Vue.js").name()).isEqualTo("Vue");
            assertThat(VOCABULARY.normalize(".env file").name()).isEqualTo("Dotenv");
        }

        @Test
        @DisplayName("should merge IDE internals that are not languages")
        void shouldMergeNonLanguages_whenValueIsIdeInternal() {
            assertThat(VOCABULARY.normalize("textmate")).isEqualTo(LanguageVocabulary.OTHER);
            assertThat(VOCABULARY.normalize("ARCHIVE")).isEqualTo(LanguageVocabulary.OTHER);
            assertThat(VOCABULARY.normalize("git-commit")).isEqualTo(LanguageVocabulary.OTHER);
            assertThat(VOCABULARY.normalize("textmate").type()).isEqualTo(LanguageType.OTHER);
        }

        @Test
        @DisplayName("should preserve an unknown value instead of hiding it")
        void shouldPreserveRaw_whenValueIsUnknown() {
            CanonicalLanguage result = VOCABULARY.normalize("Zig");

            // Preserving the raw name keeps a genuinely new language visible; folding it into Other
            // would bury it in a bucket nobody inspects.
            assertThat(result.name()).isEqualTo("Zig");
            assertThat(result.recognized()).isFalse();
            assertThat(result.type()).isEqualTo(LanguageType.OTHER);
        }

        @Test
        @DisplayName("should not report a value that could forge a log line")
        void shouldNotReport_whenValueCarriesControlCharacters() {
            // The value is client-supplied and used to be logged verbatim, so a newline would let a
            // crafted language name append a line of its own to the server log.
            String forged = "Zig\nWARN  forged line";

            assertThat(VOCABULARY.normalize(forged).name()).isEqualTo(forged);
            assertThat(VOCABULARY.unmappedValues()).doesNotContain(forged);
        }

        @Test
        @DisplayName("should not report an implausibly long value")
        void shouldNotReport_whenValueIsTooLong() {
            String oversized = "Z".repeat(200);

            assertThat(VOCABULARY.normalize(oversized).recognized()).isFalse();
            assertThat(VOCABULARY.unmappedValues()).doesNotContain(oversized);
        }

        @Test
        @DisplayName("should report a plausible unknown value")
        void shouldReport_whenValueLooksLikeALanguage() {
            VOCABULARY.normalize("Zig");
            VOCABULARY.normalize("Nim");

            assertThat(VOCABULARY.unmappedValues()).contains("Zig", "Nim");
        }

        @Test
        @DisplayName("should tolerate blank input")
        void shouldNotFail_whenValueIsBlank() {
            assertThat(VOCABULARY.normalize(null).recognized()).isFalse();
            assertThat(VOCABULARY.normalize("   ").recognized()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
            "JAVA, PROGRAMMING",
            "Kotlin, PROGRAMMING",
            "shellscript, PROGRAMMING",
            "Markdown, PROSE",
            "HTML, MARKUP",
            "CSS, MARKUP",
            "JSON, DATA",
            "YAML, DATA",
            "SQL, DATA"
        })
        @DisplayName("should classify a value with its Linguist category")
        void shouldClassifyType_whenKnown(String raw, LanguageType expected) {
            assertThat(VOCABULARY.normalize(raw).type()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("coverage of what the IDEs actually emit")
    class CoverageTests {

        @ParameterizedTest
        @MethodSource("observedValues")
        @DisplayName("should classify every value observed in a real session store")
        void shouldRecognize_whenValueWasObservedInRealData(String raw) {
            assertThat(VOCABULARY.normalize(raw).recognized())
                    .as("'%s' must be classified, not left unknown", raw)
                    .isTrue();
        }

        static Stream<String> observedValues() {
            return OBSERVED_JETBRAINS_VALUES.stream();
        }

        @Test
        @DisplayName("should classify every file type the JetBrains IDEs declare")
        void shouldRecognize_whenValueIsAJetbrainsFileType() {
            assertThat(unrecognizedIn("language/jetbrains-filetypes.txt")).isEmpty();
        }

        @Test
        @DisplayName("should classify every language id VS Code declares")
        void shouldRecognize_whenValueIsAVsCodeLanguageId() {
            assertThat(unrecognizedIn("language/vscode-languageids.txt")).isEmpty();
        }

        @Test
        @DisplayName("should report an observed value count close to the language count")
        void shouldNotInflateLanguageCount_whenValuesAreSpellingsOfOneLanguage() {
            // The defect this vocabulary exists for: distinct raw spellings must not each count as
            // a separate language. 14 observed values collapse because some are the same language
            // aliased, and several are IDE internals that are not languages at all.
            long distinct =
                    OBSERVED_JETBRAINS_VALUES.stream()
                            .map(VOCABULARY::normalize)
                            .filter(language -> language.type() != LanguageType.OTHER)
                            .map(CanonicalLanguage::name)
                            .distinct()
                            .count();

            assertThat(distinct).isLessThan(OBSERVED_JETBRAINS_VALUES.size());
        }
    }

    @Nested
    @DisplayName("resource integrity")
    class ResourceIntegrityTests {

        @Test
        @DisplayName("should reject an alias that points at an unknown language")
        void shouldRejectAlias_whenTargetIsMissing() {
            LanguageVocabulary.VocabularyFile broken =
                    new LanguageVocabulary.VocabularyFile(
                            1,
                            java.util.Map.of("Java", "programming"),
                            java.util.Map.of("java", "Jva"),
                            java.util.List.of());

            assertThatThrownBy(() -> new LanguageVocabulary(broken))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Jva");
        }

        @Test
        @DisplayName("should expose every canonical language for filter endpoints")
        void shouldExposeLanguages_whenAsked() {
            // Sortedness is a property of the name, not of the record — languages have no natural
            // order of their own.
            assertThat(VOCABULARY.languages()).isNotEmpty();
            assertThat(VOCABULARY.languages())
                    .extracting(CanonicalLanguage::name)
                    .isSorted()
                    .contains("Java", "Kotlin", "TypeScript", "Shell");
            assertThat(VOCABULARY.languages())
                    .doesNotContain(LanguageVocabulary.OTHER)
                    .allMatch(CanonicalLanguage::recognized);
            assertThat(VOCABULARY.version()).isPositive();
        }
    }

    private static List<String> unrecognizedIn(String classpathResource) {
        return readLines(classpathResource).stream()
                .filter(value -> !VOCABULARY.normalize(value).recognized())
                .toList();
    }

    /** Reads a fixture list, ignoring its comment header and blank lines. */
    private static List<String> readLines(String classpathResource) {
        try {
            return new ClassPathResource(classpathResource)
                    .getContentAsString(StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + classpathResource, e);
        }
    }
}
