package com.ahogek.cttserver.auth.passkey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * Minimal ES256 virtual authenticator for end-to-end WebAuthn tests.
 *
 * <p>It produces the exact browser JSON shape consumed by the Yubico parser, with an {@code
 * attestation=none} statement. Test-only; never use it as product code.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
final class PasskeyTestAuthenticator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());
    private static final int CREDENTIAL_ID_BYTES = 32;
    private static final int P256_COORDINATE_BYTES = 32;

    private final String rpId;
    private final String origin;

    PasskeyTestAuthenticator(String rpId, String origin) {
        this.rpId = rpId;
        this.origin = origin;
    }

    Registration register(JsonNode options, UUID userId) throws Exception {
        JsonNode publicKey = options.path("publicKey");
        String challenge = publicKey.path("challenge").asText();
        byte[] credentialId = new byte[CREDENTIAL_ID_BYTES];
        RANDOM.nextBytes(credentialId);
        KeyPair keyPair = generateKeyPair();
        byte[] userHandle = userHandle(userId);
        byte[] clientData = clientData("webauthn.create", challenge);
        byte[] authData = registrationAuthData(credentialId, keyPair);
        byte[] attestationObject = attestationObject(authData);

        ObjectNode root = JSON.createObjectNode();
        root.put("id", base64Url(credentialId));
        root.put("rawId", base64Url(credentialId));
        root.put("type", "public-key");
        ObjectNode response = root.putObject("response");
        response.put("clientDataJSON", base64Url(clientData));
        response.put("attestationObject", base64Url(attestationObject));
        root.putObject("clientExtensionResults");
        return new Registration(credentialId, userHandle, keyPair, root);
    }

    Registration reRegister(JsonNode options, Registration existing) throws Exception {
        JsonNode publicKey = options.path("publicKey");
        String challenge = publicKey.path("challenge").asText();
        byte[] clientData = clientData("webauthn.create", challenge);
        byte[] authData = registrationAuthData(existing.credentialId(), existing.keyPair());
        byte[] attestationObject = attestationObject(authData);

        ObjectNode root = JSON.createObjectNode();
        root.put("id", base64Url(existing.credentialId()));
        root.put("rawId", base64Url(existing.credentialId()));
        root.put("type", "public-key");
        ObjectNode response = root.putObject("response");
        response.put("clientDataJSON", base64Url(clientData));
        response.put("attestationObject", base64Url(attestationObject));
        root.putObject("clientExtensionResults");
        return new Registration(
                existing.credentialId(), existing.userHandle(), existing.keyPair(), root);
    }

    String assertCredential(JsonNode options, Registration registration, long signCount)
            throws Exception {
        return assertCredential(options, registration, signCount, true);
    }

    String assertCredential(
            JsonNode options, Registration registration, long signCount, boolean userVerified)
            throws Exception {
        JsonNode publicKey = options.path("publicKey");
        String challenge = publicKey.path("challenge").asText();
        byte[] clientData = clientData("webauthn.get", challenge);
        byte[] authData = assertionAuthData(signCount, userVerified);
        byte[] clientDataHash = sha256(clientData);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(registration.keyPair().getPrivate());
        signer.update(authData);
        signer.update(clientDataHash);
        byte[] signature = signer.sign();

        ObjectNode root = JSON.createObjectNode();
        root.put("id", base64Url(registration.credentialId()));
        root.put("rawId", base64Url(registration.credentialId()));
        root.put("type", "public-key");
        ObjectNode response = root.putObject("response");
        response.put("authenticatorData", base64Url(authData));
        response.put("clientDataJSON", base64Url(clientData));
        response.put("signature", base64Url(signature));
        response.put("userHandle", base64Url(registration.userHandle()));
        root.putObject("clientExtensionResults");
        return JSON.writeValueAsString(root);
    }

    private byte[] registrationAuthData(byte[] credentialId, KeyPair keyPair) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(32 + 1 + 4 + 16 + 2 + credentialId.length + 256);
        buffer.put(rpIdHash);
        buffer.put((byte) 0x45); // UP | UV | AT
        buffer.putInt(0);
        buffer.put(new byte[16]); // AAGUID
        buffer.putShort((short) credentialId.length);
        buffer.put(credentialId);
        buffer.put(coseKey(keyPair));
        return trim(buffer);
    }

    private byte[] assertionAuthData(long signCount, boolean userVerified) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(37);
        buffer.put(rpIdHash);
        buffer.put((byte) (userVerified ? 0x05 : 0x01)); // UP, optionally UV
        buffer.putInt((int) signCount);
        return buffer.array();
    }

    private byte[] attestationObject(byte[] authData) throws Exception {
        Map<String, Object> attestation = new LinkedHashMap<>();
        attestation.put("fmt", "none");
        attestation.put("attStmt", Map.of());
        attestation.put("authData", authData);
        return CBOR.writeValueAsBytes(attestation);
    }

    private byte[] coseKey(KeyPair keyPair) throws Exception {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        Map<Integer, Object> cose = new LinkedHashMap<>();
        cose.put(1, 2); // EC2
        cose.put(3, -7); // ES256
        cose.put(-1, 1); // P-256
        cose.put(-2, fixed(publicKey.getW().getAffineX()));
        cose.put(-3, fixed(publicKey.getW().getAffineY()));
        return CBOR.writeValueAsBytes(cose);
    }

    private byte[] clientData(String type, String challenge) throws Exception {
        ObjectNode data = JSON.createObjectNode();
        data.put("type", type);
        data.put("challenge", challenge);
        data.put("origin", origin);
        data.put("crossOrigin", false);
        return JSON.writeValueAsBytes(data);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == P256_COORDINATE_BYTES) {
            return raw;
        }
        byte[] fixed = new byte[P256_COORDINATE_BYTES];
        if (raw.length > P256_COORDINATE_BYTES) {
            System.arraycopy(
                    raw, raw.length - P256_COORDINATE_BYTES, fixed, 0, P256_COORDINATE_BYTES);
        } else {
            System.arraycopy(raw, 0, fixed, P256_COORDINATE_BYTES - raw.length, raw.length);
        }
        return fixed;
    }

    private static byte[] userHandle(UUID userId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(userId.getMostSignificantBits());
        buffer.putLong(userId.getLeastSignificantBits());
        return buffer.array();
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static byte[] trim(ByteBuffer buffer) {
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    record Registration(
            byte[] credentialId, byte[] userHandle, KeyPair keyPair, ObjectNode responseJson) {}
}
