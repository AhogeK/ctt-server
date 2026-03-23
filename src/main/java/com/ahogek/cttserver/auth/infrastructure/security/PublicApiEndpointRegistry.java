package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic registry for public API endpoints.
 *
 * <p>Scans all controllers at application startup to collect URLs marked with {@link PublicApi}
 * annotation. These URLs are then used by Spring Security to configure the public whitelist.
 *
 * <p>Implements "Secure by Default" pattern: all endpoints are protected unless explicitly marked
 * as public.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class PublicApiEndpointRegistry {

    private static final Logger log = LoggerFactory.getLogger(PublicApiEndpointRegistry.class);

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final Set<String> publicUrls = new HashSet<>();

    public PublicApiEndpointRegistry(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @PostConstruct
    public void init() {
        // System endpoints
        publicUrls.add("/error");
        publicUrls.add("/actuator/health");
        publicUrls.add("/actuator/info");

        // API documentation (Springdoc OpenAPI)
        publicUrls.add("/swagger-ui.html");
        publicUrls.add("/swagger-ui/**");
        publicUrls.add("/v3/api-docs/**");

        Map<RequestMappingInfo, HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        handlerMethods.forEach(
                (info, method) -> {
                    if (isPublicApi(method)) {
                        Set<String> patterns = extractPatterns(info);
                        publicUrls.addAll(patterns);
                        patterns.forEach(
                                url -> log.atInfo().log("Registered public API route: {}", url));
                    }
                });

        log.atInfo().log("Public API registry initialized with {} routes", publicUrls.size());
    }

    /**
     * Checks if the handler method or its declaring class is marked with {@link PublicApi}.
     *
     * @param method the handler method to check
     * @return true if the endpoint should be public
     */
    private boolean isPublicApi(HandlerMethod method) {
        return method.getMethod().isAnnotationPresent(PublicApi.class)
                || method.getBeanType().isAnnotationPresent(PublicApi.class);
    }

    /**
     * Extracts URL patterns from RequestMappingInfo using PathPatternsRequestCondition.
     *
     * <p>Uses Spring 6.0+ PathPatternParser which pre-compiles routes into AST for O(L) matching
     * performance, replacing the deprecated AntPathMatcher-based PatternsRequestCondition.
     *
     * @param info the request mapping info
     * @return set of URL patterns
     */
    private Set<String> extractPatterns(RequestMappingInfo info) {
        Set<String> patterns = new HashSet<>();

        var pathPatternsCondition = info.getPathPatternsCondition();
        if (pathPatternsCondition != null) {
            patterns.addAll(pathPatternsCondition.getPatternValues());
        }

        return patterns;
    }

    /**
     * Returns the array of public URL patterns for Spring Security configuration.
     *
     * @return array of public URL patterns
     */
    public String[] getPublicUrls() {
        return publicUrls.toArray(new String[0]);
    }

    /**
     * Returns the set of public URL patterns (for testing purposes).
     *
     * @return set of public URL patterns
     */
    public Set<String> getPublicUrlSet() {
        return new HashSet<>(publicUrls);
    }
}
