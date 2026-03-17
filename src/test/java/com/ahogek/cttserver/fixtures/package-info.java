/**
 * Test fixtures package providing Object Mother and Builder patterns for test data creation.
 *
 * <p><b>Usage guidelines:</b>
 *
 * <ul>
 *   <li><b>Object Mother</b>: Use named factory methods for common test scenarios (e.g., {@code
 *       UserFixtures.regularUser()})
 *   <li><b>Builder</b>: Chain methods for custom test data (e.g., {@code
 *       UserFixtures.builder().email("custom@test.com").build()})
 *   <li><b>Persisted</b>: Use {@link PersistedFixtures} for database operations in Repository and
 *       Integration tests
 * </ul>
 *
 * <p><b>Scope:</b>
 *
 * <ul>
 *   <li>Service/Controller Slice tests: Direct fixture.build() without persistence
 *   <li>Repository tests: Use PersistedFixtures with TestEntityManager
 *   <li>Integration tests: Use PersistedFixtures for full database context
 * </ul>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
package com.ahogek.cttserver.fixtures;
