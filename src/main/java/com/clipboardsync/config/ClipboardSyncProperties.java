package com.clipboardsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Runtime settings for the ClipboardSync relay.
 *
 * @param websocketPath path that accepts client WebSocket connections
 * @param allowedOrigins allowed WebSocket origins, usually restricted in production
 * @param maxCiphertextBytes maximum accepted ciphertext size in bytes after Base64 decoding
 * @param devicePublicKeysDir optional directory containing one Base64 Ed25519 public key file per device
 * @param devicePublicKeys per-device Base64 encoded Ed25519 public keys used during the WebSocket handshake
 */
@ConfigurationProperties(prefix = "clipboard-sync")
public record ClipboardSyncProperties(
        String websocketPath,
        String[] allowedOrigins,
        int maxCiphertextBytes,
        String devicePublicKeysDir,
        Map<String, String> devicePublicKeys
) {
    private static final String DEVICE_PUBLIC_KEY_ENV_PREFIX = "CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_";
    private static final String DEFAULT_GROUP_ID = "default";

    /**
     * Creates validated relay configuration.
     */
    public ClipboardSyncProperties {
        devicePublicKeys = mergedDevicePublicKeys(devicePublicKeys, devicePublicKeysDir);
        if (devicePublicKeys == null || devicePublicKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one clipboard-sync.device-public-keys entry is required");
        }
        devicePublicKeys.forEach((deviceId, publicKey) -> {
            if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(publicKey)) {
                throw new IllegalArgumentException("Device public key entries must have non-empty device IDs and keys");
            }
        });
    }

    /**
     * Finds the Base64 encoded public key registered for a group/device pair.
     *
     * <p>Entries may be keyed as {@code groupId.deviceId}, {@code groupId/deviceId}, or
     * {@code groupId__deviceId}. A bare {@code deviceId} key is treated as part of the
     * {@code default} group for single-user compatibility.</p>
     *
     * @param groupId synchronization group identifier
     * @param deviceId device identifier inside the group
     * @return registered public key when present
     */
    public Optional<String> publicKeyFor(String groupId, String deviceId) {
        return Optional.ofNullable(devicePublicKeys.get(groupDeviceKey(groupId, deviceId)));
    }

    private static Map<String, String> mergedDevicePublicKeys(Map<String, String> configuredKeys, String directory) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.putAll(loadDirectoryDevicePublicKeys(directory));
        if (configuredKeys != null) {
            configuredKeys.forEach((key, value) -> merged.put(normalizeDevicePublicKeyName(key), value));
        }
        merged.putAll(environmentDevicePublicKeys());
        return merged;
    }

    private static Map<String, String> environmentDevicePublicKeys() {
        Map<String, String> merged = new LinkedHashMap<>();
        System.getenv().forEach((name, value) -> {
            if (name.startsWith(DEVICE_PUBLIC_KEY_ENV_PREFIX)) {
                String key = normalizeDevicePublicKeyName(name.substring(DEVICE_PUBLIC_KEY_ENV_PREFIX.length()));
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    merged.put(key, value);
                }
            }
        });
        return merged;
    }

    private static Map<String, String> loadDirectoryDevicePublicKeys(String directory) {
        Map<String, String> loaded = new LinkedHashMap<>();
        if (!StringUtils.hasText(directory)) {
            return loaded;
        }
        Path directoryPath = Path.of(directory);
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("Device public keys directory does not exist: " + directory);
        }
        try (Stream<Path> paths = Files.walk(directoryPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> loaded.put(
                            normalizeDevicePublicKeyName(directoryPath.relativize(path).toString()),
                            readPublicKey(path)
                    ));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read device public keys directory: " + directory, exception);
        }
        return loaded;
    }

    private static String readPublicKey(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read device public key file: " + path, exception);
        }
    }

    private static String normalizeDevicePublicKeyName(String name) {
        String normalized = name.replace('\\', '/').replace("__", "/");
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            return groupDeviceKey(parts[0], parts[1]);
        }
        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.", 2);
            return groupDeviceKey(parts[0], parts[1]);
        }
        return groupDeviceKey(DEFAULT_GROUP_ID, normalized);
    }

    private static String groupDeviceKey(String groupId, String deviceId) {
        if (!StringUtils.hasText(groupId) || !StringUtils.hasText(deviceId)) {
            return "";
        }
        String normalizedDeviceId = Arrays.stream(deviceId.replace('\\', '/').split("/"))
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second)
                .orElse(deviceId);
        return groupId + "/" + normalizedDeviceId;
    }
}
