package com.ahogek.cttserver.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Paginated response wrapper for list endpoints.
 *
 * <p>This class provides standard pagination metadata following
 * common industry conventions (RFC 5988 or offset/limit based).</p>
 *
 * <p>Response format:</p>
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PagedResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    private PagedResponse(Builder<T> builder) {
        this.items = builder.items;
        this.page = builder.page;
        this.size = builder.size;
        this.totalItems = builder.totalItems;
        this.totalPages = calculateTotalPages(builder.totalItems, builder.size);
        this.hasNext = builder.page < this.totalPages;
        this.hasPrevious = builder.page > 1;
    }

    private static int calculateTotalPages(long totalItems, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / size);
    }

    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return PagedResponse.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .build();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public List<T> items() {
        return items;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public long totalItems() {
        return totalItems;
    }

    public int totalPages() {
        return totalPages;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public boolean hasPrevious() {
        return hasPrevious;
    }

    public static final class Builder<T> {
        private List<T> items;
        private int page = 1;
        private int size = 20;
        private long totalItems = 0;

        private Builder() {}

        public Builder<T> items(List<T> items) {
            this.items = items;
            return this;
        }

        public Builder<T> page(int page) {
            this.page = page;
            return this;
        }

        public Builder<T> size(int size) {
            this.size = size;
            return this;
        }

        public Builder<T> totalItems(long totalItems) {
            this.totalItems = totalItems;
            return this;
        }

        public PagedResponse<T> build() {
            return new PagedResponse<>(this);
        }
    }
}
