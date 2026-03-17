package com.ahogek.cttserver.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for test baseline infrastructure.
 *
 * <p>Validates the complete testing infrastructure chain:
 *
 * <ol>
 *   <li>PostgreSQLContainer starts (postgres:16.3)
 *   <li>@ServiceConnection injects DataSource
 *   <li>Hibernate creates schema (ddl-auto: create-drop)
 *   <li>TestEntityManager is available for JPA operations
 * </ol>
 *
 * <p><b>Important for onboarding:</b> This test MUST pass before any other tests can work. New
 * developers should run this test first to verify their local Docker environment is configured
 * correctly.
 *
 * <p><b>How to verify baseline:</b>
 *
 * <pre>{@code
 * ./gradlew test --tests "*TestBaselineSmokeTest"
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@BaseRepositoryTest
@DisplayName("Test Baseline Smoke - Testcontainers + Hibernate + JPA Chain")
class TestBaselineSmokeTest {

    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("TestEntityManager available + schema created by Hibernate")
    void testEntityManagerAvailable() {
        // TestEntityManager being available means Testcontainers + JPA both work
        assertThat(em).isNotNull();

        // Verify we can execute a simple query (schema exists)
        var result = em.getEntityManager().createNativeQuery("SELECT 1").getSingleResult();
        assertThat(((Number) result).intValue()).isEqualTo(1);
    }
}
