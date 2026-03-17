package com.ahogek.cttserver.common;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base annotation for Controller Slice tests.
 *
 * <p>Provides a standardized configuration for testing Spring MVC controllers in isolation. This
 * annotation wraps @WebMvcTest for controller slice testing with standardized test profile
 * activation.
 *
 * <p><b>Key features:</b>
 *
 * <ul>
 *   <li>Loads only Web layer beans (controllers, controllers advice, filters)
 *   <li>Activates "test" profile for test-specific configuration
 *   <li>Supports controller isolation for faster test startup
 *   <li>Supports excludeFilters to skip unnecessary components (e.g., AOP aspects)
 * </ul>
 *
 * <p><b>Phase 1 (Current)</b>: Use @WithMockUser for authenticated endpoints, or exclude security
 * filters via excludeFilters if needed.
 *
 * <p><b>Phase 2 (After auth implementation)</b>: Use @WithMockUser for authenticated tests.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * @BaseControllerSliceTest(UserController.class)
 * @DisplayName("User Controller Tests")
 * class UserControllerTest {
 *     @Autowired MockMvcTester mvc;
 *     @MockitoBean UserService userService;
 *
 *     @Test
 *     @WithMockUser
 *     void getUser_whenExists_returns200() {
 *         assertThat(mvc.get().uri("/api/users/1"))
 *                 .hasStatusOk();
 *     }
 * }
 *
 * // With excludeFilters to skip global aspects
 * @BaseControllerSliceTest(
 *     value = UserController.class,
 *     excludeFilters = @ComponentScan.Filter(
 *         type = FilterType.ASSIGNABLE_TYPE,
 *         classes = {RateLimitAspect.class, IdempotentAspect.class}
 *     )
 * )
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 * @see WebMvcTest
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@ActiveProfiles("test")
public @interface BaseControllerSliceTest {

    /**
     * Specifies the controllers to test. If empty, all @Controller beans will be loaded.
     *
     * <p>For better test isolation and faster startup, specify only the controllers needed.
     *
     * <p><b>Aliased to @WebMvcTest.controllers</b>
     *
     * @return the controller classes to test
     */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};

    /**
     * Specifies which components to exclude from scanning.
     *
     * <p>Useful for excluding global aspects (e.g., RateLimitAspect, IdempotentAspect) that may
     * interfere with isolated controller tests.
     *
     * <p><b>Aliased to @WebMvcTest.excludeFilters</b>
     *
     * @return the component scan filters to exclude
     */
    @AliasFor(annotation = WebMvcTest.class, attribute = "excludeFilters")
    ComponentScan.Filter[] excludeFilters() default {};
}
