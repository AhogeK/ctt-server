package com.ahogek.cttserver.auth.passkey.config;

import com.ahogek.cttserver.auth.passkey.service.PasskeyAuthenticationService;
import com.ahogek.cttserver.auth.passkey.service.PasskeyCredentialRepository;
import com.ahogek.cttserver.common.config.properties.PasskeyProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;

/**
 * Wires the Yubico relying party to this application's configuration and credential repository.
 *
 * <p>Signature-counter validation is disabled in the library and implemented in {@link
 * PasskeyAuthenticationService} because synced passkeys legitimately report a counter of zero.
 * Attestation is not evaluated: {@code attestation=none} is the product contract, and attestation
 * trust is not an access-control decision for this public service.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Configuration
public class PasskeyConfig {

    /**
     * Builds the relying party from validated passkey properties.
     *
     * @param properties relying-party ID, origins, name and ceremony settings
     * @param credentials credential repository backed by the account table
     * @return the configured relying party
     */
    @Bean
    public RelyingParty relyingParty(
            PasskeyProperties properties, PasskeyCredentialRepository credentials) {
        return RelyingParty.builder()
                .identity(
                        RelyingPartyIdentity.builder()
                                .id(properties.rpId())
                                .name(properties.rpName())
                                .build())
                .credentialRepository(credentials)
                .origins(Set.copyOf(properties.allowedOrigins()))
                .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .allowUntrustedAttestation(true)
                .allowOriginPort(false)
                .allowOriginSubdomain(false)
                .validateSignatureCounter(false)
                .build();
    }
}
