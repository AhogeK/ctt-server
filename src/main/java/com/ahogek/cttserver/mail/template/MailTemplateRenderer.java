package com.ahogek.cttserver.mail.template;

/**
 * Renders email templates from strongly-typed data objects.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
public interface MailTemplateRenderer {

    /**
     * Renders HTML email content from the provided template data.
     *
     * @param data the template data containing variables
     * @return rendered HTML string
     */
    String renderHtml(MailTemplateData data);

    /**
     * Renders plain text email content from the provided template data.
     *
     * @param data the template data containing variables
     * @return rendered plain text string
     */
    String renderText(MailTemplateData data);
}
