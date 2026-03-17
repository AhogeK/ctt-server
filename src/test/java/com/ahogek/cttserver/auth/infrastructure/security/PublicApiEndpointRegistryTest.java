package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PublicApiEndpointRegistry.
 *
 * <p>Uses a test-specific controller to verify the registry correctly identifies public endpoints.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PublicApiEndpointRegistryTest {

    @Autowired private PublicApiEndpointRegistry registry;

    @Test
    void baseWhitelist_includesSystemEndpoints() {
        Set<String> publicUrls = registry.getPublicUrlSet();

        assertThat(publicUrls).contains("/error", "/actuator/health", "/actuator/info");
    }

    @Test
    void baseWhitelist_includesSwaggerEndpoints() {
        Set<String> publicUrls = registry.getPublicUrlSet();

        assertThat(publicUrls).contains("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**");
    }

    @Test
    void classLevelPublicApi_allMethodsArePublic() {
        Set<String> publicUrls = registry.getPublicUrlSet();

        assertThat(publicUrls).contains("/api/test/public/all");
    }

    @Test
    void methodLevelPublicApi_onlyAnnotatedMethodsArePublic() {
        Set<String> publicUrls = registry.getPublicUrlSet();

        assertThat(publicUrls)
                .contains("/api/test/mixed/public")
                .doesNotContain("/api/test/mixed/protected");
    }

    @Test
    void protectedController_noUrlsArePublic() {
        Set<String> publicUrls = registry.getPublicUrlSet();

        assertThat(publicUrls).isNotEmpty().doesNotContain("/api/test/protected/data");
    }

    @Test
    void getPublicUrls_returnsArray() {
        String[] urls = registry.getPublicUrls();

        assertThat(urls).isNotEmpty().contains("/error");
    }

    /**
     * Test controllers for verifying PublicApi registry functionality.
     *
     * <p>Only active in 'test' profile to avoid polluting production.
     */
    @TestConfiguration
    @Profile("test")
    static class TestControllers {

        @RestController
        @RequestMapping("/api/test/public")
        @PublicApi(reason = "Test class-level public API")
        static class TestPublicController {
            @GetMapping("/all")
            public String getAll() {
                return "all";
            }
        }

        @RestController
        @RequestMapping("/api/test/mixed")
        static class TestMixedController {
            @PublicApi(reason = "Test method-level public API")
            @GetMapping("/public")
            public String getPublic() {
                return "public";
            }

            @GetMapping("/protected")
            public String getProtected() {
                return "protected";
            }
        }

        @RestController
        @RequestMapping("/api/test/protected")
        static class TestProtectedController {
            @GetMapping("/data")
            public String getData() {
                return "data";
            }
        }
    }
}
