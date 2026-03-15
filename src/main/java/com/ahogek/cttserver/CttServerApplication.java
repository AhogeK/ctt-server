package com.ahogek.cttserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application entry point for CTT Server.
 *
 * <p><strong>Enabled Features:</strong>
 *
 * <ul>
 *   <li>Async processing via {@code @EnableAsync}
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@SpringBootApplication
@EnableAsync
public class CttServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CttServerApplication.class, args);
    }
}
