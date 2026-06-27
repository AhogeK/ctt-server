package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountBinding;
import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountsResponse;
import com.ahogek.cttserver.auth.oauth.service.OAuthAccountQueryService;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
 * OAuth account binding query REST controller.
 *
 * <p>Exposes the list of third-party OAuth accounts bound to the authenticated user. Sensitive
 * fields (access token, refresh token, provider user ID) are intentionally excluded from the
 * response payload.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@Tag(name = "OAuth", description = "GitHub OAuth authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthAccountController {

    private final OAuthAccountQueryService oauthAccountQueryService;
    private final CurrentUserProvider currentUserProvider;

    public OAuthAccountController(
            OAuthAccountQueryService oauthAccountQueryService,
            CurrentUserProvider currentUserProvider) {
        this.oauthAccountQueryService = oauthAccountQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "List OAuth account bindings",
            description =
                    """
                    Returns all third-party OAuth accounts bound to the authenticated user. \
                    Response never contains access tokens, refresh tokens, or provider user IDs.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OAuth account bindings retrieved successfully",
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
                                                                  "code": "AUTH_002",
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-06-28T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/accounts")
    public ResponseEntity<RestApiResponse<OAuthAccountsResponse>> listAccounts() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        List<OAuthAccountBinding> bindings = oauthAccountQueryService.listBindings(userId);

        return ResponseEntity.ok(RestApiResponse.ok(new OAuthAccountsResponse(bindings)));
    }
}