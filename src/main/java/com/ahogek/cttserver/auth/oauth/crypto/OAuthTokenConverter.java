package com.ahogek.cttserver.auth.oauth.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JPA AttributeConverter for transparent OAuth token encryption/decryption.
 *
 * <p>Automatically encrypts tokens before database storage and decrypts after retrieval. Uses
 * setter injection for {@link OAuthTokenEncryptor} to support Spring-managed converter instances in
 * Hibernate 5+.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-12
 */
@Component
@Converter
public class OAuthTokenConverter implements AttributeConverter<String, String> {

    private OAuthTokenEncryptor encryptor;

    @Autowired
    public void setEncryptor(OAuthTokenEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!StringUtils.hasText(attribute)) {
            return attribute;
        }
        if (encryptor == null) {
            throw new IllegalStateException("OAuthTokenEncryptor is not initialized in converter.");
        }
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return dbData;
        }
        if (encryptor == null) {
            throw new IllegalStateException("OAuthTokenEncryptor is not initialized in converter.");
        }
        return encryptor.decrypt(dbData);
    }
}
