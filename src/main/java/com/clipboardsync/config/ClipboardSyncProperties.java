package com.clipboardsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Runtime settings for the ClipboardSync relay.
 *
 * @param websocketPath path that accepts client WebSocket connections
 * @param allowedOrigins allowed WebSocket origins, usually restricted in production
 * @param maxCiphertextBytes maximum accepted ciphertext size in bytes after Base64 decoding
 * @param devicePublicKeys per-device Base64 encoded Ed25519 public keys used during the WebSocket handshake
 */
@ConfigurationProperties(prefix = "clipboard-sync")
public record ClipboardSyncProperties(
        String websocketPath,
        String[] allowedOrigins,
        int maxCiphertextBytes,
        Map<String, String> devicePublicKeys
) {
    /**
     * Creates validated relay configuration.
     */
    public ClipboardSyncProperties {
        if (devicePublicKeys == null || devicePublicKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one clipboard-sync.device-public-keys entry is required");
        }
        devicePublicKeys.forEach((deviceId, publicKey) -> {
            if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(publicKey)) {
                throw new IllegalArgumentException("Device public key entries must have non-empty device IDs and keys");
            }
        });
    }
}
