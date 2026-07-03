package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountBinding;
import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountsResponse;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.service.OAuthAccountQueryService;
import com.ahogek.cttserver.auth.oauth.service.OAuthLoginOrRegisterService;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
 * OAuth account binding REST controller.
 *
 * <p>Exposes two operations on the authenticated user's OAuth bindings:
 *
 * <ul>
 *   <li>{@code GET /accounts} — list all bindings (query)
 *   <li>{@code DELETE /accounts/{provider}} — unbind a single provider (mutation)
 * </ul>
 *
 * <p>Sensitive fields (access token, refresh token, provider user ID) are intentionally excluded
 * from any response payload.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@Tag(name = "OAuth", description = "GitHub OAuth authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthAccountController {

    private final OAuthAccountQueryService oauthAccountQueryService;
    private final OAuthLoginOrRegisterService oauthLoginOrRegisterService;
    private final CurrentUserProvider currentUserProvider;

    public OAuthAccountController(
            OAuthAccountQueryService oauthAccountQueryService,
            OAuthLoginOrRegisterService oauthLoginOrRegisterService,
            CurrentUserProvider currentUserProvider) {
        this.oauthAccountQueryService = oauthAccountQueryService;
        this.oauthLoginOrRegisterService = oauthLoginOrRegisterService;
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

    @Operation(
            summary = "Unbind an OAuth provider from the current user",
            description =
                    """
                    Removes the binding between the authenticated user and the specified OAuth \
                    provider. The provider-side OAuth grant is left untouched (this is a local \
                    unbind only). The user's session is not affected — JWT and refresh token \
                    remain valid and unchanged.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "OAuth binding removed",
                        content = @Content),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid provider - COMMON_001: Unsupported OAuth provider",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalid-provider",
                                                        summary = "Unsupported OAuth provider",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_001",
                                                                  "message": "Invalid request parameters",
                                                                  "details": [
                                                                    {
                                                                      "field": "provider",
                                                                      "message": "Unsupported OAuth provider: google"
                                                                    }
                                                                  ],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-06-29T10:00:00Z"
                                                                }
                                                                """))),
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
                                                                  "timestamp": "2026-06-29T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Binding not found - AUTH_017: OAuth account not linked to this user",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "not-linked",
                                                        summary = "No binding for this provider",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_017",
                                                                  "message": "OAuth account not linked",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 404,
                                                                  "timestamp": "2026-06-29T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Last login method - AUTH_018: Cannot unlink the only remaining credential",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "last-method",
                                                        summary =
                                                                "User would lose all login methods",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_018",
                                                                  "message": "Cannot unlink last credential",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-06-29T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/accounts/{provider}")
    public ResponseEntity<Void> unbindAccount(@PathVariable OAuthProvider provider) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        oauthLoginOrRegisterService.unbindFromExistingUser(userId, provider);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePathVariableConversion(
            MethodArgumentTypeMismatchException ex) {
        String fieldName = ex.getName();
        String rejectedValue = String.valueOf(ex.getValue());
        ErrorResponse body =
                ErrorResponse.of(ErrorCode.COMMON_001)
                        .addDetail(fieldName, "Unsupported OAuth provider: " + rejectedValue)
                        .withTraceId(currentTraceId());
        return ResponseEntity.badRequest().body(body);
    }

    private static String currentTraceId() {
        return RequestContext.current()
                .map(RequestInfo::traceId)
                .orElseGet(() -> MDC.get("traceId"));
    }
}
