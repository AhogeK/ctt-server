/**
 * Cross-IDE programming language vocabulary.
 *
 * <p>Coding sessions arrive carrying whatever identifier the originating IDE uses: JetBrains
 * reports a {@code FileType} name ({@code "JAVA"}, {@code "GitIgnore file"}), VS Code reports a
 * {@code languageId} ({@code "typescript"}, {@code "shellscript"}). The same language therefore
 * arrives under different spellings, which would split one language into several buckets in every
 * aggregation, distribution and achievement that groups by it.
 *
 * <p>This package owns the single normalization step that maps a raw identifier to a canonical
 * language. Names and categories follow GitHub Linguist, so the vocabulary is defined by an
 * externally maintained standard rather than by whichever IDE happened to be implemented first.
 *
 * @since 2026-09-16
 */
package com.ahogek.cttserver.language;
