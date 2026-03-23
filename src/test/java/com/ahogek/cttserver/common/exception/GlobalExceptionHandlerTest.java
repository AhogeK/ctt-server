package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.probe.ProbeController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(
        controllers = ProbeController.class,
        excludeFilters = {
            @org.springframework.context.annotation.ComponentScan.Filter(
                    type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                    classes = {
                        com.ahogek.cttserver.common.ratelimit.RateLimitAspect.class,
                        com.ahogek.cttserver.common.idempotent.IdempotentAspect.class
                    })
        })
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvcTester mvc;

    @Test
    @WithMockUser
    void ok_returns_unified_structure() {
        assertThat(mvc.get().uri("/probe/ok"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.success")
                .isEqualTo(true);
    }

    @Test
    @WithMockUser
    void validation_failure_returns_error_response() {
        assertThat(
                        mvc.post()
                                .uri("/probe/validate")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        { "name": "", "age": 0 }
                        """))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");
    }

    @Test
    @WithMockUser
    void validation_success_returns_ok() {
        assertThat(
                        mvc.post()
                                .uri("/probe/validate")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        { "name": "test", "age": 25 }
                        """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.success")
                .isEqualTo(true);
    }

    @Test
    @WithMockUser
    void business_exception_returns_error_response() {
        assertThat(mvc.get().uri("/probe/biz-error"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_002");
    }

    @Test
    @WithMockUser
    void generic_exception_returns_500_without_stack() {
        assertThat(mvc.get().uri("/probe/sys-error"))
                .hasStatus(500)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("SYSTEM_001");
    }

    @Test
    @WithMockUser
    void malformed_json_returns_400() {
        assertThat(
                        mvc.post()
                                .uri("/probe/validate")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ broken json"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    @WithMockUser
    void missing_param_returns_400() {
        assertThat(mvc.get().uri("/probe/missing-param"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_005");
    }

    @Test
    @WithMockUser
    void illegal_argument_returns_400() {
        assertThat(mvc.get().uri("/probe/illegal-arg"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void handleConstraintViolation_returns_400() {
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException(
                        java.util.Collections.emptySet());
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        var result = handler.handleConstraintViolation(ex);
        assertThat(result.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void handleUnauthorizedException_returns_401() {
        UnauthorizedException ex = new UnauthorizedException(ErrorCode.AUTH_003, "Invalid token");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        var result = handler.handleSecurityException(ex);
        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assert result.getBody() != null;
        assertThat(result.getBody().code()).isEqualTo("AUTH_003");
    }

    @Test
    void handleForbiddenException_returns_403() {
        ForbiddenException ex = new ForbiddenException(ErrorCode.AUTH_005, "Access denied");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        var result = handler.handleSecurityException(ex);
        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assert result.getBody() != null;
        assertThat(result.getBody().code()).isEqualTo("AUTH_005");
    }

    @Test
    void handleInternalServerErrorException_returns_500() {
        InternalServerErrorException ex = new InternalServerErrorException("Internal system error");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        var result = handler.handleInternalServerError(ex);
        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assert result.getBody() != null;
        assertThat(result.getBody().code()).isEqualTo("SYSTEM_001");
    }

    @Test
    void handleDataIntegrityViolationException_returns_409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate key");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        var result = handler.handleDataIntegrityViolation(ex);
        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assert result.getBody() != null;
        assertThat(result.getBody().code()).isEqualTo("USER_001");
    }
}
