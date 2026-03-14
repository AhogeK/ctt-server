package com.ahogek.cttserver.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PagedResponseTest {

    @Test
    void of_withItems_returnsPagedResponse() {
        List<String> items = List.of("a", "b", "c");
        PagedResponse<String> response = PagedResponse.of(items, 1, 10, 100L);

        assertThat(response.items()).isEqualTo(items);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalItems()).isEqualTo(100L);
        assertThat(response.totalPages()).isEqualTo(10);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isFalse();
    }

    @Test
    void of_lastPage_hasNoNext() {
        List<String> items = List.of("a", "b");
        PagedResponse<String> response = PagedResponse.of(items, 5, 10, 50L);

        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    void of_firstPage_hasNoPrevious() {
        PagedResponse<String> response = PagedResponse.of(List.of("a"), 1, 10, 5L);

        assertThat(response.hasPrevious()).isFalse();
    }

    @Test
    void of_zeroSize_returnsZeroPages() {
        PagedResponse<String> response = PagedResponse.of(List.of(), 1, 0, 0L);

        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isFalse();
    }

    @Test
    void of_exactDivision_calculatesCorrectPages() {
        PagedResponse<String> response = PagedResponse.of(List.of(), 1, 10, 30L);

        assertThat(response.totalPages()).isEqualTo(3);
    }
}
