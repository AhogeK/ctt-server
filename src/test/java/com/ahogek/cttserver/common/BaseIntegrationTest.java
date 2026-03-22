package com.ahogek.cttserver.common;

import com.ahogek.cttserver.TestcontainersConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base annotation for Integration tests.
 *
 * <p>Starts the complete Spring ApplicationContext with all beans, configurations, and
 * Testcontainers. Use for end-to-end testing that requires multiple layers to work together.
 *
 * <p><b>Context caching:</b> All test classes using this annotation share the same
 * ApplicationContext for optimal CI performance. To maintain context reuse:
 *
 * <ul>
 *   <li>Do NOT use @DirtiesContext
 *   <li>Do NOT override beans with @TestConfiguration at class level
 *   <li>Do NOT modify static state that affects context
 * </ul>
 *
 * <p><b>Key configuration:</b>
 *
 * <ul>
 *   <li>WebEnvironment.RANDOM_PORT: Start embedded server on random port
 *   <li>@AutoConfigureMockMvc: Provide MockMvc and MockMvcTester beans
 *   <li>Import TestcontainersConfiguration: PostgreSQL + Redis containers
 *   <li>Import GreenMailTestConfiguration: Embedded SMTP server (GreenMail)
 *   <li>ActiveProfiles("test"): Test-specific configuration
 *   <li>TestPropertySource: Validate schema against Flyway migrations
 * </ul>
 *
 * <p><b>Schema strategy:</b> Uses Flyway migrations for schema creation. Hibernate's ddl-auto is
 * set to "validate" to ensure schema matches entity definitions, catching migration mismatches
 * early.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * @BaseIntegrationTest
 * @DisplayName("User API Integration Tests")
 * class UserIntegrationTest {
 *     @Autowired MockMvcTester mvc;
 *
 *     @Test
 *     @DisplayName("POST /api/users - create user end-to-end")
 *     void createUser_endToEnd_success() {
 *         assertThat(mvc.post().uri("/api/users")
 *                 .contentType(MediaType.APPLICATION_JSON)
 *                 .content("{\"email\":\"test@example.com\"}"))
 *                 .hasStatus(HttpStatus.CREATED);
 *     }
 * }
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 * @see SpringBootTest
 * @see TestcontainersConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GreenMailTestConfiguration.class, TestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
public @interface BaseIntegrationTest {}
