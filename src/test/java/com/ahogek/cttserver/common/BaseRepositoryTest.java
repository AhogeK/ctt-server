package com.ahogek.cttserver.common;

import com.ahogek.cttserver.TestcontainersConfiguration;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base annotation for Repository Slice tests.
 *
 * <p>Provides standardized configuration for testing JPA repositories with Testcontainers. Only
 * loads JPA-related beans (Repositories, EntityManager, etc.) without starting the full Spring
 * context.
 *
 * <p><b>Key configuration:</b>
 *
 * <ul>
 *   <li>@DataJpaTest: Loads only JPA slice components
 *   <li>AutoConfigureTestDatabase.Replace.NONE: Use real PostgreSQL from Testcontainers
 *   <li>Import TestcontainersConfiguration: Provides @ServiceConnection for Docker containers
 *   <li>ActiveProfiles("test"): Activates test-specific configuration (ddl-auto: create-drop)
 * </ul>
 *
 * <p><b>Schema strategy:</b> Uses Hibernate's ddl-auto: create-drop (defined in
 * application-test.yaml) for rapid test iteration. Schema is auto-generated from JPA entities, not
 * from Flyway migrations.
 *
 * <p><b>Container reuse:</b> Testcontainers can reuse containers across JVM processes for faster
 * local development. Enable by setting ~/.testcontainers.properties:
 * testcontainers.reuse.enable=true
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * @BaseRepositoryTest
 * @DisplayName("UserRepository Tests")
 * class UserRepositoryTest {
 *     @Autowired TestEntityManager em;
 *     @Autowired UserRepository userRepository;
 *
 *     @Test
 *     @DisplayName("findByEmail - case insensitive query")
 *     void findByEmail_whenExists_returnsUser() {
 *         var user = new User();
 *         user.setEmail("test@example.com");
 *         em.persistAndFlush(user);
 *
 *         var result = userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM");
 *         assertThat(result).isPresent();
 *     }
 * }
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 * @see DataJpaTest
 * @see TestcontainersConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public @interface BaseRepositoryTest {}
