package com.ahogek.cttserver.common.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Paginated response wrapper for list endpoints.
 *
 * <p>This class provides standard pagination metadata following common industry conventions (RFC
 * 5988 or offset/limit based).
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "items": [...],
 *   "page": 1,
 *   "size": 20,
 *   "totalItems": 150,
 *   "totalPages": 8,
 *   "hasNext": true,
 *   "hasPrevious": false
 * }
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@Schema(description = "Paginated response wrapper for list endpoints")
public record PagedResponse<T>(
        @Schema(description = "List of items on the current page") List<T> items,
        @Schema(description = "Current page number (1-based)", example = "1") int page,
        @Schema(description = "Number of items per page", example = "20") int size,
        @Schema(description = "Total number of items across all pages", example = "150")
                long totalItems,
        @Schema(description = "Total number of pages", example = "8") int totalPages,
        @Schema(description = "Whether a next page exists", example = "true") boolean hasNext,
        @Schema(description = "Whether a previous page exists", example = "false")
                boolean hasPrevious) {

    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = calculateTotalPages(totalItems, size);
        boolean hasNext = page < totalPages;
        boolean hasPrevious = page > 1;
        return new PagedResponse<>(items, page, size, totalItems, totalPages, hasNext, hasPrevious);
    }

    private static int calculateTotalPages(long totalItems, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / size);
    }
}
