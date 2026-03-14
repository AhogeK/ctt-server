package com.ahogek.cttserver.common.response;

import java.util.List;

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
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
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
