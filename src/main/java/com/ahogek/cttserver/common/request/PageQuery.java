package com.ahogek.cttserver.common.request;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Strictly constrained pagination query base class.
 *
 * <p>Prevents OOM and slow queries by limiting page size to maximum 100 records.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> listUsers(
 *         @Valid @ModelAttribute PageQuery pageQuery) {
 *     // pageQuery.getOffset() returns calculated SQL offset
 * }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public class PageQuery {

    @NotNull
    @Min(value = 1, message = ValidationConstants.MSG_PAGE_MIN)
    private Integer page = 1;

    @NotNull
    @Min(value = 1, message = ValidationConstants.MSG_PAGE_SIZE_MIN)
    @Max(value = 100, message = ValidationConstants.MSG_PAGE_SIZE_MAX)
    private Integer size = 20;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * Calculates SQL OFFSET value for pagination.
     *
     * @return calculated offset: (page - 1) * size
     */
    public int getOffset() {
        return (this.page - 1) * this.size;
    }
}
