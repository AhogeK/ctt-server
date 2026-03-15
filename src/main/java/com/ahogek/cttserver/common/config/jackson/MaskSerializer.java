package com.ahogek.cttserver.common.config.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

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
 * <p>This is the second line of defense in the three-layer desensitization architecture:
 *
 * <ol>
 *   <li>Filter layer: {@code DesensitizeUtils.maskHeader()}
 *   <li>DTO layer: This serializer (via {@code @JsonSerialize})
 *   <li>Global layer: {@code MaskingMessageConverter} regex兜底
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
