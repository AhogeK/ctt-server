package com.ahogek.cttserver.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * Embedded SMTP sandbox configuration using GreenMail - runs in-process without Docker and does not
 * send real emails.
 *
 * <p>Port is dynamically injected via {@link DynamicPropertyRegistrar}, overriding the placeholder
 * value in application-test.yaml.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@TestConfiguration
public class GreenMailTestConfiguration {

    @Bean
    public GreenMail greenMail() {
        // ServerSetup.port(0) = let OS assign random port to avoid CI conflicts
        ServerSetup setup = new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP);
        GreenMail greenMail =
                new GreenMail(setup)
                        .withConfiguration(
                                GreenMailConfiguration.aConfig()
                                        .withUser("test@localhost", "test", "test"));
        greenMail.start();
        return greenMail;
    }

    /**
     * DynamicPropertyRegistrar executes before ApplicationContext refresh, injecting GreenMail's
     * actual bound port into the Environment.
     */
    @Bean
    public DynamicPropertyRegistrar greenMailProperties(GreenMail greenMail) {
        return registry -> {
            registry.add("spring.mail.host", () -> "localhost");
            registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
            registry.add("spring.mail.username", () -> "test");
            registry.add("spring.mail.password", () -> "test");
        };
    }
}
