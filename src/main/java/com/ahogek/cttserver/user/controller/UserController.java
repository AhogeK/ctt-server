package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;
import com.ahogek.cttserver.user.dto.DeleteAccountRequest;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.service.AccountDeletionService;
import com.ahogek.cttserver.user.service.UserProfileService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AccountDeletionService accountDeletionService;
    private final LeaderboardService leaderboardService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(
            UserProfileService userProfileService,
            AccountDeletionService accountDeletionService,
            LeaderboardService leaderboardService,
            CurrentUserProvider currentUserProvider) {
        this.userProfileService = userProfileService;
        this.accountDeletionService = accountDeletionService;
        this.leaderboardService = leaderboardService;
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

    @Operation(
            summary = "Delete the current user's account",
            description =
                    """
                    Deletes the authenticated user's account. Identity is anonymized, every \
                    credential is revoked, and the account is removed from the leaderboards it was \
                    ranked in. Deletion is permanent: the account cannot be recovered, and the freed \
                    email address can be registered again as a new, unrelated account. \
                    Confirmed with the account password, which accounts created through an OAuth \
                    provider do not have; for those the session is the whole of the available \
                    proof. Requires a signed-in session — an API key cannot delete the account it \
                    was issued for.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Account deleted",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT, or wrong password",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidPassword",
                                                        summary = "Supplied password is incorrect",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_014",
                                                                  "message": "Invalid password",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-09-17T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Password not supplied, or the caller used an API key",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "passwordRequired",
                                                    summary =
                                                            "Account has a password, none supplied",
                                                    value =
                                                            """
                                                            {
                                                              "code": "USER_013",
                                                              "message": "Password verification required",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "sessionRequired",
                                                    summary = "Called with an API key",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_025",
                                                              "message": "This action requires a signed-in session, not an API key",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """)
                                        }))
            })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<RestApiResponse<EmptyResponse>> deleteCurrentUser(
            @Valid @RequestBody DeleteAccountRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        // An API key is a credential sitting on disk in the plugin; letting one destroy the account
        // it was issued for turns a leaked key into account loss.
        if (currentUser.authType() != CurrentUser.AuthenticationType.WEB_SESSION) {
            throw new ForbiddenException(ErrorCode.AUTH_025);
        }
        accountDeletionService.deleteAccount(currentUser.id(), request.password());
        // After the transaction, never inside it: a ranking write that outlived a rollback would
        // leave a live account unranked, whereas the reverse (deleted but still ranked) is logged
        // and visible.
        leaderboardService.removeUserFromRankings(currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok("Account deleted")));
    }
}
