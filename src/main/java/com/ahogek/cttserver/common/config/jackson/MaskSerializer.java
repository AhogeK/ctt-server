package com.ahogek.cttserver.common.config.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Jackson serializer that masks sensitive string values.
 *
 * <p>Use this serializer on DTO fields that contain passwords, tokens, or other sensitive data to
 * prevent accidental logging of raw values.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * public record UserLoginResponse(
 *     String userId,
 *     String email,
 *
 *     @JsonSerialize(using = MaskSerializer.class)
 *     String token
 * ) {}
 * }</pre>
 *
 * <p>When serialized to JSON for logging or API response, the token field will appear as: {@code
 * "token": "******"}
 *
 * <p>This serializer is the second layer of the desensitization architecture,
 * <strong>available</strong> for a field that would otherwise be serialised with a sensitive value:
 *
 * <ol>
 *   <li>Filter layer: {@code DesensitizeUtils.maskHeader()}
 *   <li>DTO layer: This serializer (via {@code @JsonSerialize}); no production field needs it
 *       today, because credentials are not serialised into responses and DTOs are not logged as a
 *       whole
 *   <li>Global layer: {@code MaskingMessageConverter} regex fallback, including the JSON encoder
 * </ol>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public class MaskSerializer extends JsonSerializer<String> {

    private static final String MASK = "******";

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeString(MASK);
    }
}
