package com.ahogek.cttserver.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or method as a public API endpoint that requires no authentication.
 *
 * <p>Endpoints marked with this annotation are automatically whitelisted in Spring Security
 * configuration. All other endpoints are secured by default (deny by default strategy).
 *
 * <p>This annotation can be applied at either the class level (making all methods public) or method
 * level (making specific methods public).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/auth")
 * public class AuthController {
 *
 *     @PublicApi(reason = "User registration endpoint")
 *     @PostMapping("/register")
 *     public ApiResponse<Void> register(...) { ... }
 * }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {

    /**
     * Optional explanation for why this endpoint is public.
     *
     * <p>Used for security audit and code review purposes.
     *
     * @return the reason for public access
     */
    String reason() default "";
}
