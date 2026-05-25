package com.clipboardsync.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
 * Loads and resolves registered client Ed25519 public keys.
 */
@Component
public class DevicePublicKeyStore {

    private static final String DEVICE_PUBLIC_KEY_ENV_PREFIX = "CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_";
    private static final String DEFAULT_GROUP_ID = "default";

    private final Map<String, String> devicePublicKeys;

    /**
     * Creates a key store from relay configuration, directory files, and environment overrides.
     *
     * @param properties relay runtime settings
     */
    @Autowired
    public DevicePublicKeyStore(ClipboardSyncProperties properties) {
        this(mergedDevicePublicKeys(properties.devicePublicKeys(), properties.devicePublicKeysDir()));
    }

    /**
     * Creates a key store from already normalized or normalizable key entries.
     *
     * @param devicePublicKeys configured public keys
     */
    public DevicePublicKeyStore(Map<String, String> devicePublicKeys) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (devicePublicKeys != null) {
            devicePublicKeys.forEach((key, value) -> {
                String normalizedKey = normalizeDevicePublicKeyName(key);
                if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(value)) {
                    normalized.put(normalizedKey, value);
                }
            });
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one clipboard-sync.device-public-keys entry is required");
        }
        this.devicePublicKeys = Map.copyOf(normalized);
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

    /**
     * Returns normalized public key entries.
     *
     * @return immutable map keyed by {@code groupId/deviceId}
     */
    public Map<String, String> devicePublicKeys() {
        return devicePublicKeys;
    }

    private static Map<String, String> mergedDevicePublicKeys(Map<String, String> configuredKeys, String directory) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.putAll(loadDirectoryDevicePublicKeys(directory));
        if (configuredKeys != null) {
            merged.putAll(configuredKeys);
        }
        merged.putAll(environmentDevicePublicKeys());
        return merged;
    }

    private static Map<String, String> environmentDevicePublicKeys() {
        Map<String, String> merged = new LinkedHashMap<>();
        System.getenv().forEach((name, value) -> {
            if (name.startsWith(DEVICE_PUBLIC_KEY_ENV_PREFIX) && StringUtils.hasText(value)) {
                merged.put(name.substring(DEVICE_PUBLIC_KEY_ENV_PREFIX.length()), value);
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
                    .forEach(path -> loaded.put(directoryPath.relativize(path).toString(), readPublicKey(path)));
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
