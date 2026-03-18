package com.ahogek.cttserver.mail;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;

import com.icegreen.greenmail.util.GreenMail;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("Mail Configuration Integration Tests")
class MailConfigurationTest {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired private GreenMail greenMail;

    @Test
    @DisplayName("JavaMailSender bean should be created from application-test.yaml")
    void javaMailSenderBeanShouldBeCreated() {
        assertThat(mailSender)
                .as("JavaMailSender bean should be created by Spring Boot auto-configuration")
                .isNotNull();
    }

    @Test
    @DisplayName("GreenMail should be running on random port")
    void greenMailShouldBeRunning() {
        assertThat(greenMail).isNotNull();
        assertThat(greenMail.getSmtp().getPort()).isGreaterThan(0);
    }

    @AfterEach
    void clearMailbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }
}
