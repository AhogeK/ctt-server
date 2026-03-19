package com.ahogek.cttserver.mail.config;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Configuration for email template rendering engine.
 *
 * <p>Provides a standalone {@link TemplateEngine} configured for email templates, separate from
 * Spring MVC's web template engine to avoid conflicts.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
@Configuration
@EnableConfigurationProperties(CttMailProperties.class)
public class MailTemplateConfig {

    /**
     * Creates a standalone TemplateEngine for email rendering.
     *
     * @return configured SpringTemplateEngine with HTML and TEXT resolvers
     */
    @Bean
    @Primary
    public SpringTemplateEngine mailTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(htmlTemplateResolver());
        engine.addTemplateResolver(textTemplateResolver());
        return engine;
    }

    private ClassLoaderTemplateResolver htmlTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setOrder(1);
        resolver.setPrefix("mail-templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        return resolver;
    }

    private ClassLoaderTemplateResolver textTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setOrder(2);
        resolver.setPrefix("mail-templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        return resolver;
    }
}
