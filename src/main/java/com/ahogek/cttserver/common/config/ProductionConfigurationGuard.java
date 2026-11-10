package com.ahogek.cttserver.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects a production configuration that would start with development values.
 *
 * <p>Four mistakes are silent without it: the OpenAPI and Swagger endpoints stay exposed, CORS
 * keeps a placeholder origin, GitHub OAuth is advertised without credentials, and a secret copied
 * from the repository keeps protecting a real deployment. Every violation is collected and reported
 * in one message, so a single restart lists everything that must be fixed.
 *
 * <p>The guard only runs under the {@code prod} profile; development and tests keep their defaults.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-10
 */
@Component
@Profile("prod")
public class ProductionConfigurationGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigurationGuard.class);

    /** Hosts and words that only make sense in a sample, never in a deployed origin or URL. */
    private static final Pattern PLACEHOLDER =
            Pattern.compile(
                    "(?i)example\\.(com|org|net)|localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0"
                            + "|changeme|change-me|change_me|placeholder|dummy|sample|your[-_.]");

    /**
     * Delimited words that mark a value as development-only. A base64 secret only matches when '/',
     * '+' or '=' happen to surround one of the words; regenerating the value resolves it.
     */
    private static final Pattern DEVELOPMENT_WORD =
            Pattern.compile(
                    "(?i)(?:^|[^a-z0-9])(?:dev|test|local|dummy|sample|change)(?:[^a-z0-9]|$)");

    /** Literals written in this repository for development, including the public TOTP fallback. */
    private static final Set<String> DEVELOPMENT_SECRETS =
            Set.of(
                    "ctt_local_pass",
                    "ctt_redis_pass",
                    "te9dquxfrevwx1rpvfbfs0vzx05pvf9gt1jfufjprfg=",
                    "admin",
                    "password",
                    "postgres",
                    "redis",
                    "secret");

    private final Environment environment;

    public ProductionConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration rejected:\n - " + String.join("\n - ", violations));
        }
        log.info(
                "Production configuration accepted: API documentation disabled, CORS and OAuth constrained");
    }

    static List<String> violations(Environment environment) {
        List<String> violations = new ArrayList<>();
        if (enabled(environment, "springdoc.api-docs.enabled")) {
            violations.add("`springdoc.api-docs.enabled` must be false in production");
        }
        if (enabled(environment, "springdoc.swagger-ui.enabled")) {
            violations.add("`springdoc.swagger-ui.enabled` must be false in production");
        }
        checkCors(environment, violations);
        checkPublicUrl(environment, "ctt.security.oauth.frontend-url", violations);
        checkPublicUrl(environment, "ctt.mail.frontend.base-url", violations);
        checkGithub(environment, violations);
        checkSecret(environment, "ctt.security.jwt.secret-key", 32, violations);
        checkSecret(environment, "ctt.security.oauth.token-encryption-key", 32, violations);
        checkSecret(environment, "spring.datasource.password", 16, violations);
        checkSecret(environment, "spring.data.redis.password", 0, violations);
        checkSecret(environment, "spring.mail.password", 0, violations);
        checkSecret(environment, "ctt.security.two-factor.encryption-key", 32, violations);
        return violations;
    }

    private static boolean enabled(Environment environment, String key) {
        return environment.getProperty(key, Boolean.class, true);
    }

    private static void checkCors(Environment environment, List<String> violations) {
        List<String> origins = list(environment, "ctt.security.cors.allowed-origins");
        List<String> patterns = list(environment, "ctt.security.cors.allowed-origin-patterns");
        if (origins.isEmpty() && patterns.isEmpty()) {
            violations.add(
                    "`ctt.security.cors.allowed-origins` and `allowed-origin-patterns` must not be"
                            + " empty");
            return;
        }
        List<String> configured = new ArrayList<>(origins);
        configured.addAll(patterns);
        for (String origin : configured) {
            if (!isPublicHttpsUrl(origin)) {
                violations.add("CORS origin must be an explicit https host, was: " + origin);
            }
        }
    }

    private static void checkPublicUrl(
            Environment environment, String key, List<String> violations) {
        String value = environment.getProperty(key);
        if (!isPublicHttpsUrl(value)) {
            violations.add("`" + key + "` must be a public https URL, was: " + value);
        }
    }

    private static void checkGithub(Environment environment, List<String> violations) {
        checkText(environment, "ctt.security.oauth.github.client-id", violations);
        checkText(environment, "ctt.security.oauth.github.client-secret", violations);
    }

    private static void checkText(Environment environment, String key, List<String> violations) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            violations.add("`" + key + "` must be set");
            return;
        }
        if (isPlaceholder(value) || DEVELOPMENT_SECRETS.contains(value.toLowerCase(Locale.ROOT))) {
            violations.add("`" + key + "` still carries a development value");
        }
    }

    private static void checkSecret(
            Environment environment, String key, int minimumLength, List<String> violations) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            violations.add("`" + key + "` must be set");
            return;
        }
        if (value.length() < minimumLength) {
            violations.add(
                    "`"
                            + key
                            + "` must be at least "
                            + minimumLength
                            + " characters in production");
            return;
        }
        if (DEVELOPMENT_SECRETS.contains(value.toLowerCase(Locale.ROOT))
                || DEVELOPMENT_WORD.matcher(value).find()) {
            violations.add("`" + key + "` still carries a development value");
        }
    }

    private static boolean isPublicHttpsUrl(String value) {
        return value != null
                && value.startsWith("https://")
                && value.length() > "https://".length()
                && !value.contains("*")
                && !isPlaceholder(value);
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDER.matcher(value).find();
    }

    private static List<String> list(Environment environment, String key) {
        return Binder.get(environment).bind(key, Bindable.listOf(String.class)).orElseGet(List::of);
    }
}
