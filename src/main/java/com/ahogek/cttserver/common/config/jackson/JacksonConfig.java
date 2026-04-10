package com.ahogek.cttserver.common.config.jackson;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Global Jackson configuration.
 *
 * <p>Provides a configured ObjectMapper bean for the entire application. This ObjectMapper is used
 * by Spring MVC for JSON serialization/deserialization and can be injected into components like
 * JwtAuthenticationEntryPoint.
 *
 * <p>JavaTimeModule is explicitly registered because Spring Boot 4's auto-configuration is
 * bypassed when a custom ObjectMapper bean is defined.
 *
 * @author AhogeK
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
