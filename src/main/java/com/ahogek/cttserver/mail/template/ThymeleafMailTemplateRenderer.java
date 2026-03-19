package com.ahogek.cttserver.mail.template;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Thymeleaf-based implementation of {@link MailTemplateRenderer}.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
@Service
public class ThymeleafMailTemplateRenderer implements MailTemplateRenderer {

    private final TemplateEngine emailTemplateEngine;

    /**
     * Constructs a new ThymeleafMailTemplateRenderer with the specified template engine.
     *
     * @param emailTemplateEngine the standalone TemplateEngine for email rendering
     */
    public ThymeleafMailTemplateRenderer(TemplateEngine emailTemplateEngine) {
        this.emailTemplateEngine = emailTemplateEngine;
    }

    @Override
    public String renderHtml(MailTemplateData data) {
        Context context = new Context();
        context.setVariables(data.getVariables());
        return emailTemplateEngine.process(data.getTemplateName(), context);
    }

    @Override
    public String renderText(MailTemplateData data) {
        Context context = new Context();
        context.setVariables(data.getVariables());
        // Explicitly append .txt to ensure the TEXT resolver is used
        return emailTemplateEngine.process(data.getTemplateName() + ".txt", context);
    }
}
