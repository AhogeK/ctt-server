package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TermsCheckFilter registration in SecurityFilterChain.
 *
 * <p>Verifies that TermsCheckFilter is properly injected into SecurityConfig and added to the
 * security filter chain at the correct position (after SecurityContextHolderFilter).
 *
 * @author AhogeK
 * @since 2026-05-05
 */
@BaseIntegrationTest
@DisplayName("TermsCheckFilter Registration Integration Tests")
class SecurityConfigTermsFilterTest {

    @Autowired private SecurityFilterChain securityFilterChain;

    @Autowired private TermsCheckFilter termsCheckFilter;

    @Test
    @DisplayName("Should register TermsCheckFilter in SecurityFilterChain")
    void shouldRegisterTermsCheckFilter() {
        List<jakarta.servlet.Filter> filters = securityFilterChain.getFilters();

        assertThat(filters).isNotNull();
        assertThat(filters).anyMatch(filter -> filter instanceof TermsCheckFilter);
    }

    @Test
    @DisplayName("Should position TermsCheckFilter after SecurityContextHolderFilter")
    void shouldPositionTermsCheckFilterCorrectly() {
        List<jakarta.servlet.Filter> filters = securityFilterChain.getFilters();

        int termsCheckFilterIndex = -1;
        int securityContextHolderFilterIndex = -1;

        for (int i = 0; i < filters.size(); i++) {
            jakarta.servlet.Filter filter = filters.get(i);
            if (filter instanceof TermsCheckFilter) {
                termsCheckFilterIndex = i;
            }
            if (filter.getClass().getSimpleName().contains("SecurityContextHolder")) {
                securityContextHolderFilterIndex = i;
            }
        }

        assertThat(termsCheckFilterIndex).isGreaterThan(-1);
        assertThat(securityContextHolderFilterIndex).isGreaterThan(-1);
        assertThat(termsCheckFilterIndex)
                .isGreaterThan(securityContextHolderFilterIndex)
                .describedAs(
                        "TermsCheckFilter should be positioned after SecurityContextHolderFilter");
    }

    @Test
    @DisplayName("Should inject TermsCheckFilter bean into SecurityConfig")
    void shouldInjectTermsCheckFilterBean() {
        assertThat(termsCheckFilter).isNotNull();
    }
}
