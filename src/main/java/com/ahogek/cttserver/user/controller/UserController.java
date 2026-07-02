package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.service.UserProfileService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Current user profile REST controller.
 *
 * <p>Exposes the authenticated user's profile information. Sensitive fields (password hash, last
 * login IP, JPA @Version) are intentionally excluded from the response payload.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-01
 */
@Tag(name = "User", description = "User profile and account management")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(
            UserProfileService userProfileService, CurrentUserProvider currentUserProvider) {
        this.userProfileService = userProfileService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Get current user profile",
            description =
                    """
                    Returns the authenticated user's profile including id, email, displayName, \
                    emailVerified, createdAt, lastLoginAt, and termsVersion. Sensitive fields \
                    such as passwordHash, lastLoginIp, and the JPA @Version are never exposed.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User profile retrieved successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid JWT",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_001",
                                                                  "message": "Authentication required",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<RestApiResponse<UserProfileResponse>> getCurrentUserProfile() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        UserProfileResponse profile = userProfileService.getCurrentUserProfile(userId);
        return ResponseEntity.ok(RestApiResponse.ok(profile));
    }
}
