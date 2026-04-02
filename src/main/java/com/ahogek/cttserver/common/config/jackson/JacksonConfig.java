package com.ahogek.cttserver.common.config.jackson;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Global Jackson configuration.
 *
 * <p>Provides a configured ObjectMapper bean for the entire application. This ObjectMapper is used
 * by Spring MVC for JSON serialization/deserialization and can be injected into components like
 * JwtAuthenticationEntryPoint.
 *
 * @author AhogeK
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
