package com.ahogek.cttserver.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Client IP resolution")
class IpUtilsTest {

    @Test
    @DisplayName("Should ignore forwarded headers for an untrusted peer")
    void shouldIgnoreForwardedHeaders_whenPeerIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("X-Real-IP", "198.51.100.98");

        assertThat(IpUtils.getRealIp(request)).isEqualTo("203.0.113.10");
        assertThat(IpUtils.getRealIp(request, Set.of("10.0.0.1"))).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Should use the rightmost untrusted forwarded hop behind a trusted proxy")
    void shouldUseRightmostUntrustedHop_whenPeerIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.7");

        assertThat(IpUtils.getRealIp(request, Set.of("10.0.0.10"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Should not let a client prepend a spoofed hop")
    void shouldIgnoreSpoofedLeftmostHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertThat(IpUtils.getRealIp(request, Set.of("10.0.0.10"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Should normalize IPv6 loopback")
    void shouldNormalizeIpv6Loopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");

        assertThat(IpUtils.getRealIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should normalize the expanded IPv6 loopback form")
    void shouldNormalizeExpandedIpv6Loopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        assertThat(IpUtils.getRealIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should trust HTTPS forwarding only from a trusted proxy")
    void shouldTreatForwardedHttpsAsSecure_onlyForTrustedProxy() {
        MockHttpServletRequest trusted = new MockHttpServletRequest();
        trusted.setRemoteAddr("10.0.0.10");
        trusted.addHeader("X-Forwarded-Proto", "https");

        MockHttpServletRequest untrusted = new MockHttpServletRequest();
        untrusted.setRemoteAddr("203.0.113.10");
        untrusted.addHeader("X-Forwarded-Proto", "https");

        assertThat(IpUtils.isSecureRequest(trusted, Set.of("10.0.0.10"))).isTrue();
        assertThat(IpUtils.isSecureRequest(untrusted, Set.of("10.0.0.10"))).isFalse();
    }

    @Test
    @DisplayName("Should preserve a direct TLS request as secure")
    void shouldTreatDirectTlsAsSecure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);

        assertThat(IpUtils.isSecureRequest(request, Set.of())).isTrue();
    }
}
