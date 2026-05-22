package com.clipboardsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
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
    private static final String DEVICE_PUBLIC_KEY_ENV_PREFIX = "CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_";

    /**
     * Creates validated relay configuration.
     */
    public ClipboardSyncProperties {
        devicePublicKeys = withEnvironmentDevicePublicKeys(devicePublicKeys);
        if (devicePublicKeys == null || devicePublicKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one clipboard-sync.device-public-keys entry is required");
        }
        devicePublicKeys.forEach((deviceId, publicKey) -> {
            if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(publicKey)) {
                throw new IllegalArgumentException("Device public key entries must have non-empty device IDs and keys");
            }
        });
    }

    private static Map<String, String> withEnvironmentDevicePublicKeys(Map<String, String> configuredKeys) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (configuredKeys != null) {
            merged.putAll(configuredKeys);
        }
        System.getenv().forEach((name, value) -> {
            if (name.startsWith(DEVICE_PUBLIC_KEY_ENV_PREFIX)) {
                String deviceId = name.substring(DEVICE_PUBLIC_KEY_ENV_PREFIX.length());
                if (StringUtils.hasText(deviceId) && StringUtils.hasText(value)) {
                    merged.put(deviceId, value);
                }
            }
        });
        return merged;
    }
}
