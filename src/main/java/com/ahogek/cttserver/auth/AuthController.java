package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.response.ApiResponse;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;
import com.ahogek.cttserver.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication REST controller.
 *
 * <p>Thin controller responsible only for:
 *
 * <ul>
 *   <li>Protocol conversion (HTTP to Java objects)
 *   <li>DTO syntax validation via {@code @Valid}
 *   <li>Delegating to application service layer
 * </ul>
 *
 * <p>All business logic and domain rules are handled by {@link UserService}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * User registration endpoint.
     *
     * <p>Two-layer validation:
     *
     * <ol>
     *   <li>Syntax validation: {@code @Valid} triggers JSR-380 validation on DTO
     *   <li>Semantic validation: {@link UserService} validates domain rules
     * </ol>
     *
     * @param request the registration request (validated)
     * @return success response
     */
    @PublicApi(reason = "User registration endpoint - Tier 1 public API")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<EmptyResponse>> register(
            @Valid @RequestBody UserRegisterRequest request) {

        userService.registerUser(request);

        return ResponseEntity.ok(ApiResponse.ok(EmptyResponse.ok("User registered successfully")));
    }
}
