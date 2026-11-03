package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;
import com.ahogek.cttserver.auth.passkey.repository.UserPasskeyRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.yubico.webauthn.data.exception.Base64UrlException;

/**
 * Adapts the account credential table to the Yubico WebAuthn repository contract.
 *
 * <p>User handles are the 128-bit UUID of the account, so a discoverable assertion can identify the
 * owner without trusting a client-supplied username.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Component
public class PasskeyCredentialRepository implements CredentialRepository {

    private final UserPasskeyRepository passkeys;
    private final UserRepository users;

    public PasskeyCredentialRepository(UserPasskeyRepository passkeys, UserRepository users) {
        this.passkeys = passkeys;
        this.users = users;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return users.findByEmailIgnoreCase(username)
                .map(
                        user ->
                                passkeys.findAllByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                                        .map(this::descriptor)
                                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByEmailIgnoreCase(username)
                .map(user -> PasskeyUserHandle.toByteArray(user.getId()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return PasskeyUserHandle.toUuid(userHandle).flatMap(users::findById).map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<UUID> userId = PasskeyUserHandle.toUuid(userHandle);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        return passkeys.findByCredentialId(credentialId.getBase64Url())
                .filter(passkey -> passkey.getUser().getId().equals(userId.get()))
                .map(this::registeredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeys.findByCredentialId(credentialId.getBase64Url())
                .map(passkey -> Set.of(registeredCredential(passkey)))
                .orElse(Set.of());
    }

    private PublicKeyCredentialDescriptor descriptor(UserPasskey passkey) {
        return PublicKeyCredentialDescriptor.builder()
                .id(credentialId(passkey))
                .type(PublicKeyCredentialType.PUBLIC_KEY)
                .transports(transports(passkey))
                .build();
    }

    private RegisteredCredential registeredCredential(UserPasskey passkey) {
        return RegisteredCredential.builder()
                .credentialId(credentialId(passkey))
                .userHandle(PasskeyUserHandle.toByteArray(passkey.getUser().getId()))
                .publicKeyCose(new ByteArray(passkey.getPublicKeyCose()))
                .signatureCount(passkey.getSignCount())
                .build();
    }

    private ByteArray credentialId(UserPasskey passkey) {
        try {
            return ByteArray.fromBase64Url(passkey.getCredentialId());
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Stored passkey credential ID is invalid", e);
        }
    }

    private static Set<AuthenticatorTransport> transports(UserPasskey passkey) {
        List<String> values = passkey.getTransports();
        if (values == null) {
            return Set.of();
        }
        return values.stream().map(AuthenticatorTransport::of).collect(Collectors.toSet());
    }
}
