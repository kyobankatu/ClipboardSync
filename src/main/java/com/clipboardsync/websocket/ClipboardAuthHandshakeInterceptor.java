package com.clipboardsync.websocket;

import com.clipboardsync.config.DevicePublicKeyStore;
import com.clipboardsync.relay.ClipboardRelayService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates WebSocket handshakes and binds a connection to a device identifier.
 */
@Component
public class ClipboardAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final String DEVICE_ID_HEADER = "X-Clipboard-Device-Id";
    private static final String GROUP_ID_HEADER = "X-Clipboard-Group-Id";
    private static final String TIMESTAMP_HEADER = "X-Clipboard-Timestamp";
    private static final String NONCE_HEADER = "X-Clipboard-Nonce";
    private static final String SIGNATURE_HEADER = "X-Clipboard-Signature";
    private static final String SIGNATURE_ALGORITHM = "Ed25519";
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final DevicePublicKeyStore publicKeyStore;
    private final Clock clock;
    private final Map<String, Instant> acceptedNonces = new ConcurrentHashMap<>();

    /**
     * Creates the handshake interceptor.
     *
     * @param publicKeyStore registered device public keys
     */
    @Autowired
    public ClipboardAuthHandshakeInterceptor(DevicePublicKeyStore publicKeyStore) {
        this(publicKeyStore, Clock.systemUTC());
    }

    /**
     * Creates the handshake interceptor with an explicit clock for tests.
     *
     * @param publicKeyStore registered device public keys
     * @param clock current time source
     */
    ClipboardAuthHandshakeInterceptor(DevicePublicKeyStore publicKeyStore, Clock clock) {
        this.publicKeyStore = publicKeyStore;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String groupId = firstNonBlank(
                request.getHeaders().getFirst(GROUP_ID_HEADER),
                queryParam(request, "groupId").orElse(null)
        );
        String deviceId = firstNonBlank(
                request.getHeaders().getFirst(DEVICE_ID_HEADER),
                queryParam(request, "deviceId").orElse(null)
        );
        if (!StringUtils.hasText(groupId) || !StringUtils.hasText(deviceId)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        if (!isAuthorized(request, groupId, deviceId)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ClipboardRelayService.GROUP_ID_ATTRIBUTE, groupId);
        attributes.put(ClipboardRelayService.DEVICE_ID_ATTRIBUTE, deviceId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private boolean isAuthorized(ServerHttpRequest request, String groupId, String deviceId) {
        String publicKey = publicKeyStore.publicKeyFor(groupId, deviceId).orElse(null);
        if (!StringUtils.hasText(publicKey)) {
            return false;
        }
        HttpHeaders headers = request.getHeaders();
        String timestamp = headers.getFirst(TIMESTAMP_HEADER);
        String nonce = headers.getFirst(NONCE_HEADER);
        String signature = headers.getFirst(SIGNATURE_HEADER);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            return false;
        }
        if (!isFresh(timestamp)) {
            return false;
        }
        String signingInput = signingInput(groupId, deviceId, timestamp, nonce, request.getURI().getRawPath());
        if (!verifySignature(publicKey, signingInput, signature)) {
            return false;
        }
        return acceptNonce(groupId, deviceId, nonce);
    }

    private boolean isFresh(String timestamp) {
        try {
            Instant requestedAt = Instant.parse(timestamp);
            Duration age = Duration.between(requestedAt, Instant.now(clock)).abs();
            return age.compareTo(MAX_CLOCK_SKEW) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String signingInput(String groupId, String deviceId, String timestamp, String nonce, String path) {
        return "v1\n"
                + "groupId=" + groupId + "\n"
                + "deviceId=" + deviceId + "\n"
                + "timestamp=" + timestamp + "\n"
                + "nonce=" + nonce + "\n"
                + "path=" + path + "\n";
    }

    private boolean acceptNonce(String groupId, String deviceId, String nonce) {
        removeExpiredNonces();
        String nonceKey = groupId + ":" + deviceId + ":" + nonce;
        return acceptedNonces.putIfAbsent(nonceKey, Instant.now(clock)) == null;
    }

    private void removeExpiredNonces() {
        Instant expiresBefore = Instant.now(clock).minus(MAX_CLOCK_SKEW);
        acceptedNonces.entrySet().removeIf(entry -> entry.getValue().isBefore(expiresBefore));
    }

    private boolean verifySignature(String publicKeyBase64, String signingInput, String signatureBase64) {
        try {
            PublicKey publicKey = decodePublicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private PublicKey decodePublicKey(String publicKeyBase64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
        return KeyFactory.getInstance(SIGNATURE_ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
    }

    private Optional<String> queryParam(ServerHttpRequest request, String name) {
        String query = request.getURI().getRawQuery();
        if (!StringUtils.hasText(query)) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && name.equals(urlDecode(parts[0]))) {
                return Optional.of(urlDecode(parts[1]));
            }
        }
        return Optional.empty();
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
