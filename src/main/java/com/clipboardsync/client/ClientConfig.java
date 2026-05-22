package com.clipboardsync.client;

import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
    private static final String CONFIG_PATH_ENV = "CLIPBOARD_SYNC_CLIENT_CONFIG";

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
        Properties fileProperties = loadClientProperties(environment);
        URI serverUri = URI.create(required(environment, fileProperties, "CLIPBOARD_SYNC_SERVER_URL", "serverUrl"));
        String deviceId = required(environment, fileProperties, "CLIPBOARD_SYNC_DEVICE_ID", "deviceId");
        PrivateKey privateKey = decodePrivateKey(required(
                environment,
                fileProperties,
                "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY",
                "ed25519PrivateKey"
        ));
        byte[] e2eKey = Base64.getDecoder().decode(required(environment, fileProperties, "CLIPBOARD_SYNC_E2E_KEY", "e2eKey"));
        String websocketPath = value(environment, fileProperties, "CLIPBOARD_SYNC_WEBSOCKET_PATH", "websocketPath");
        if (websocketPath == null || websocketPath.isBlank()) {
            websocketPath = pathOrDefault(serverUri);
        }
        return new ClientConfig(serverUri, websocketPath, deviceId, privateKey, e2eKey);
    }

    private static String required(Map<String, String> environment, Properties properties, String envName, String propertyName) {
        String value = value(environment, properties, envName, propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required client configuration value: " + propertyName);
        }
        return value;
    }

    private static String value(Map<String, String> environment, Properties properties, String envName, String propertyName) {
        String environmentValue = environment.get(envName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return properties.getProperty(propertyName);
    }

    private static String pathOrDefault(URI serverUri) {
        String path = serverUri.getRawPath();
        return path == null || path.isBlank() ? DEFAULT_WEBSOCKET_PATH : path;
    }

    private static PrivateKey decodePrivateKey(String privateKeyBase64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(privateKeyBase64);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static Properties loadClientProperties(Map<String, String> environment) {
        Path path = clientConfigPath(environment);
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        warnIfInsecurePermissions(path);
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to load client config file: " + path, exception);
        }
    }

    private static Path clientConfigPath(Map<String, String> environment) {
        String configured = environment.get(CONFIG_PATH_ENV);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = environment.get("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "ClipboardSync", "client.properties");
            }
        }
        return Path.of(System.getProperty("user.home"), ".config", "clipboardsync", "client.properties");
    }

    private static void warnIfInsecurePermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                System.err.println("Warning: client config file is readable or writable by group/others: " + path);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystems, including common Windows setups, are allowed in this initial CLI.
        }
    }
}
