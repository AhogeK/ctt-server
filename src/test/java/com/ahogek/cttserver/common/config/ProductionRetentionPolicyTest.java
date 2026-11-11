package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production retention policy")
class ProductionRetentionPolicyTest {

    @Test
    @DisplayName("Should keep audit rows for 180 days when the production profile is active")
    void shouldKeepAuditRowsFor180Days_whenTheProductionProfileIsActive() {
        assertThat(retentionDays("--spring.profiles.active=prod")).isEqualTo(180);
    }

    @Test
    @DisplayName("Should let the environment disable deletion in production")
    void shouldDisableDeletion_whenProductionOverridesWithZero() {
        assertThat(retentionDays("--spring.profiles.active=prod", "--AUDIT_RETENTION_DAYS=0"))
                .isZero();
    }

    @Test
    @DisplayName("Should let the environment override the production period")
    void shouldOverrideThePeriod_whenProductionIsGivenThirtyDays() {
        assertThat(retentionDays("--spring.profiles.active=prod", "--AUDIT_RETENTION_DAYS=30"))
                .isEqualTo(30);
    }

    @Test
    @DisplayName("Should keep every row when the production profile is not active")
    void shouldKeepEveryRow_whenTheProductionProfileIsNotActive() {
        assertThat(retentionDays()).isZero();
    }

    /**
     * Starts a real application context so config data applies its documented precedence: the
     * profile-specific {@code application-prod.yaml} overrides {@code application.yaml}, and an
     * environment variable overrides both.
     */
    private int retentionDays(String... commandLineArguments) {
        SpringApplication application = new SpringApplication(RetentionProbe.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        String[] arguments = new String[commandLineArguments.length + 2];
        System.arraycopy(commandLineArguments, 0, arguments, 0, commandLineArguments.length);
        arguments[commandLineArguments.length] = "--spring.main.log-startup-info=false";
        arguments[commandLineArguments.length + 1] = "--logging.level.root=OFF";
        try (ConfigurableApplicationContext context = application.run(arguments)) {
            return Binder.get(context.getEnvironment())
                    .bind("ctt.audit.retention", Bindable.of(AuditRetentionProperties.class))
                    .orElseThrow(
                            () -> new IllegalStateException("ctt.audit.retention did not bind"))
                    .days();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RetentionProbe {}
}
