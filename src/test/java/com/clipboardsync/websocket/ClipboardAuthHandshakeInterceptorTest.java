package com.clipboardsync.websocket;

import com.clipboardsync.config.DevicePublicKeyStore;
import com.clipboardsync.relay.ClipboardRelayService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClipboardAuthHandshakeInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");
    private static final String PATH = "/ws/clipboard";
    private static final String GROUP = "group-a";

    @Test
    void acceptsKnownDeviceWithValidSignature() throws Exception {
        KeyPair keyPair = ed25519KeyPair();
        ClipboardAuthHandshakeInterceptor interceptor = interceptor(Map.of("group-a.mac", publicKey(keyPair)));
        MockHttpServletRequest servletRequest = request("mac", NOW.toString(), "nonce-1", signature("mac", keyPair));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                null,
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(ClipboardRelayService.GROUP_ID_ATTRIBUTE, GROUP);
        assertThat(attributes).containsEntry(ClipboardRelayService.DEVICE_ID_ATTRIBUTE, "mac");
    }

    @Test
    void rejectsUnknownDevice() throws Exception {
        KeyPair keyPair = ed25519KeyPair();
        ClipboardAuthHandshakeInterceptor interceptor = interceptor(Map.of("group-a.mac", publicKey(keyPair)));
        MockHttpServletRequest servletRequest = request("windows", NOW.toString(), "nonce-1", signature("windows", keyPair));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsSignatureFromDifferentPrivateKey() throws Exception {
        KeyPair registeredKeyPair = ed25519KeyPair();
        KeyPair attackerKeyPair = ed25519KeyPair();
        ClipboardAuthHandshakeInterceptor interceptor = interceptor(Map.of("group-a.mac", publicKey(registeredKeyPair)));
        MockHttpServletRequest servletRequest = request("mac", NOW.toString(), "nonce-1", signature("mac", attackerKeyPair));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        KeyPair keyPair = ed25519KeyPair();
        ClipboardAuthHandshakeInterceptor interceptor = interceptor(Map.of("group-a.mac", publicKey(keyPair)));
        String timestamp = NOW.minusSeconds(3600).toString();
        MockHttpServletRequest servletRequest = request("mac", timestamp, "nonce-1", signature("mac", keyPair, timestamp));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsReusedNonce() throws Exception {
        KeyPair keyPair = ed25519KeyPair();
        ClipboardAuthHandshakeInterceptor interceptor = interceptor(Map.of("group-a.mac", publicKey(keyPair)));
        MockHttpServletRequest firstRequest = request("mac", NOW.toString(), "nonce-1", signature("mac", keyPair));
        MockHttpServletRequest secondRequest = request("mac", NOW.toString(), "nonce-1", signature("mac", keyPair));

        boolean firstAccepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(firstRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                new HashMap<>()
        );
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        boolean secondAccepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(secondRequest),
                new ServletServerHttpResponse(secondResponse),
                null,
                new HashMap<>()
        );

        assertThat(firstAccepted).isTrue();
        assertThat(secondAccepted).isFalse();
        assertThat(secondResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private ClipboardAuthHandshakeInterceptor interceptor(Map<String, String> devicePublicKeys) {
        return new ClipboardAuthHandshakeInterceptor(
                new DevicePublicKeyStore(devicePublicKeys),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static MockHttpServletRequest request(
            String deviceId,
            String timestamp,
            String nonce,
            String signature
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.addHeader("X-Clipboard-Group-Id", GROUP);
        request.addHeader("X-Clipboard-Device-Id", deviceId);
        request.addHeader("X-Clipboard-Timestamp", timestamp);
        request.addHeader("X-Clipboard-Nonce", nonce);
        request.addHeader("X-Clipboard-Signature", signature);
        return request;
    }

    private static KeyPair ed25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String publicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private static String signature(String deviceId, KeyPair keyPair) throws Exception {
        return signature(deviceId, keyPair, NOW.toString());
    }

    private static String signature(String deviceId, KeyPair keyPair, String timestamp) throws Exception {
        String signingInput = "v1\n"
                + "groupId=" + GROUP + "\n"
                + "deviceId=" + deviceId + "\n"
                + "timestamp=" + timestamp + "\n"
                + "nonce=nonce-1\n"
                + "path=" + PATH + "\n";
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }
}
