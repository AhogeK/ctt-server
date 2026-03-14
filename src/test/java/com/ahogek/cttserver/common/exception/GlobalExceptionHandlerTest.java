package com.ahogek.cttserver.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.ahogek.cttserver.probe.ProbeController;

@WebMvcTest(controllers = ProbeController.class)
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
}
