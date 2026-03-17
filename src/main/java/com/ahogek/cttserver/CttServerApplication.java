package com.ahogek.cttserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

/**
 * Main application entry point for CTT Server.
 *
 * <p><strong>Enabled Features:</strong>
 *
 * <ul>
 *   <li>Async processing via {@code @EnableAsync}
 *   <li>Global UTC timezone enforcement for consistent timestamp handling
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@SpringBootApplication
@EnableAsync
public class CttServerApplication {

    /**
     * Forces JVM default timezone to UTC before Spring Boot initialization.
     *
     * <p>This must be executed before {@link SpringApplication#run} to ensure all timestamp
     * calculations (Instant, OffsetDateTime) are consistent throughout the application lifecycle,
     * including early Spring components like logging and environment configuration.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CttServerApplication.class, args);
    }
}
