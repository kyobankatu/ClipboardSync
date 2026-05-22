package com.clipboardsync.client;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Runtime configuration required by the development CLI client.
 *
 * @param serverUri relay WebSocket URI
 * @param websocketPath path included in the Ed25519 handshake signature
 * @param deviceId stable local device identifier
 * @param ed25519PrivateKey private key used only on this client device
 * @param e2eKey raw XChaCha20-Poly1305 key shared by trusted client devices
 */
public record ClientConfig(
        URI serverUri,
        String websocketPath,
        String deviceId,
        PrivateKey ed25519PrivateKey,
        byte[] e2eKey
) {
    private static final String DEFAULT_WEBSOCKET_PATH = "/ws/clipboard";

    /**
     * Loads client configuration from environment variables.
     *
     * @return client configuration
     * @throws GeneralSecurityException if the private key cannot be decoded
     */
    public static ClientConfig fromEnvironment() throws GeneralSecurityException {
        return fromEnvironment(System.getenv());
    }

    /**
     * Loads client configuration from a provided environment map.
     *
     * @param environment environment-like key/value map
     * @return client configuration
     * @throws GeneralSecurityException if the private key cannot be decoded
     */
    public static ClientConfig fromEnvironment(Map<String, String> environment) throws GeneralSecurityException {
        URI serverUri = URI.create(required(environment, "CLIPBOARD_SYNC_SERVER_URL"));
        String deviceId = required(environment, "CLIPBOARD_SYNC_DEVICE_ID");
        PrivateKey privateKey = decodePrivateKey(required(environment, "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY"));
        byte[] e2eKey = Base64.getDecoder().decode(required(environment, "CLIPBOARD_SYNC_E2E_KEY"));
        String websocketPath = environment.getOrDefault("CLIPBOARD_SYNC_WEBSOCKET_PATH", pathOrDefault(serverUri));
        return new ClientConfig(serverUri, websocketPath, deviceId, privateKey, e2eKey);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String pathOrDefault(URI serverUri) {
        String path = serverUri.getRawPath();
        return path == null || path.isBlank() ? DEFAULT_WEBSOCKET_PATH : path;
    }

    private static PrivateKey decodePrivateKey(String privateKeyBase64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(privateKeyBase64);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }
}
