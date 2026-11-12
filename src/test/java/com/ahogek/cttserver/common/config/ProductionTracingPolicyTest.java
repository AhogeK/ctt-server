package com.ahogek.cttserver.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tracing policy each profile starts with.
 *
 * <p>The production values are a reviewed default, not a deployment runtime detail: an environment
 * variable can override them, but a deployment that ships without them must still sample and
 * export. Starting a real context keeps the documented precedence intact (profile over base,
 * environment over both), which a hand-built map would not prove.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
@DisplayName("Production tracing policy")
class ProductionTracingPolicyTest {

    @Test
    @DisplayName("Should sample one request in ten when the production profile is active")
    void shouldSampleOneInTen_whenTheProductionProfileIsActive() {
        assertThat(samplingProbability(Map.of(), "--spring.profiles.active=prod")).isEqualTo(0.1d);
    }

    @Test
    @DisplayName("Should export W3C spans when the production profile is active")
    void shouldExportW3cSpans_whenTheProductionProfileIsActive() {
        Map<String, String> environment = Map.of();
        String[] arguments = {"--spring.profiles.active=prod"};

        assertThat(property("management.tracing.export.enabled", environment, arguments))
                .isEqualTo("true");
        assertThat(property("management.tracing.propagation.produce[0]", environment, arguments))
                .isEqualTo("W3C");
        assertThat(property("management.tracing.propagation.consume[0]", environment, arguments))
                .isEqualTo("W3C");
    }

    @Test
    @DisplayName("Should keep every trace and no exporter when the base profile is active")
    void shouldKeepEveryTraceAndNoExporter_whenTheBaseProfileIsActive() {
        Map<String, String> environment = Map.of();

        assertThat(samplingProbability(environment)).isEqualTo(1.0d);
        assertThat(property("management.tracing.export.enabled", environment)).isEqualTo("false");
        assertThat(property("management.opentelemetry.tracing.export.otlp.endpoint", environment))
                .isEqualTo("http://127.0.0.1:4318/v1/traces");
    }

    @Test
    @DisplayName("Should let the environment override the production ratio")
    void shouldOverrideTheRatio_whenTheEnvironmentSetsAQuarter() {
        assertThat(
                        samplingProbability(
                                Map.of("TRACING_SAMPLING_PROBABILITY", "0.25"),
                                "--spring.profiles.active=prod"))
                .isEqualTo(0.25d);
    }

    private double samplingProbability(
            Map<String, String> environment, String... commandLineArguments) {
        return Double.parseDouble(
                property(
                        "management.tracing.sampling.probability",
                        environment,
                        commandLineArguments));
    }

    private String property(
            String key, Map<String, String> environment, String... commandLineArguments) {
        SpringApplication application = new SpringApplication(TracingProbe.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        application.setEnvironment(environmentWith(environment));
        String[] arguments = new String[commandLineArguments.length + 2];
        System.arraycopy(commandLineArguments, 0, arguments, 0, commandLineArguments.length);
        arguments[commandLineArguments.length] = "--spring.main.log-startup-info=false";
        arguments[commandLineArguments.length + 1] = "--logging.level.root=OFF";
        try (ConfigurableApplicationContext context = application.run(arguments)) {
            return context.getEnvironment().getProperty(key);
        }
    }

    /**
     * Builds an environment whose only variables are the ones a test supplies, so a variable name
     * the configuration does not use fails the test instead of passing on a machine that happens to
     * export it.
     */
    private StandardEnvironment environmentWith(Map<String, String> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        if (!variables.isEmpty()) {
            Map<String, Object> source = new LinkedHashMap<>(variables);
            environment
                    .getPropertySources()
                    .addLast(
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                    source));
        }
        return environment;
    }

    @Configuration(proxyBeanMethods = false)
    static class TracingProbe {}
}
