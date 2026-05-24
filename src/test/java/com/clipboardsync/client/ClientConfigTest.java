package com.clipboardsync.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRequiredValuesFromEnvironment() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Map<String, String> environment = Map.of(
                "CLIPBOARD_SYNC_SERVER_URL", "wss://relay.example.com/ws/clipboard",
                "CLIPBOARD_SYNC_GROUP_ID", "alice",
                "CLIPBOARD_SYNC_DEVICE_ID", "mac",
                "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                "CLIPBOARD_SYNC_E2E_KEY", Base64.getEncoder().encodeToString(new byte[32])
        );

        ClientConfig config = ClientConfig.fromEnvironment(environment);

        assertThat(config.serverUri().toString()).isEqualTo("wss://relay.example.com/ws/clipboard");
        assertThat(config.websocketPath()).isEqualTo("/ws/clipboard");
        assertThat(config.groupId()).isEqualTo("alice");
        assertThat(config.deviceId()).isEqualTo("mac");
        assertThat(config.e2eKey()).hasSize(32);
        assertThat(config.clipboardPollInterval().toMillis()).isEqualTo(500);
    }

    @Test
    void rejectsMissingRequiredValue() {
        assertThatThrownBy(() -> ClientConfig.fromEnvironment(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverUrl");
    }

    @Test
    void loadsValuesFromPropertiesFile() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path configPath = tempDir.resolve("client.properties");
        Files.writeString(configPath, ""
                + "serverUrl=wss://relay.example.com/ws/clipboard\n"
                + "groupId=alice\n"
                + "deviceId=mac\n"
                + "ed25519PrivateKey=" + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()) + "\n"
                + "e2eKey=" + Base64.getEncoder().encodeToString(new byte[32]) + "\n"
                + "clipboardPollIntervalMillis=750\n");

        ClientConfig config = ClientConfig.fromEnvironment(Map.of("CLIPBOARD_SYNC_CLIENT_CONFIG", configPath.toString()));

        assertThat(config.serverUri().toString()).isEqualTo("wss://relay.example.com/ws/clipboard");
        assertThat(config.deviceId()).isEqualTo("mac");
        assertThat(config.clipboardPollInterval().toMillis()).isEqualTo(750);
    }

    @Test
    void environmentOverridesPropertiesFile() throws Exception {
        KeyPair fileKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair envKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path configPath = tempDir.resolve("client.properties");
        Files.writeString(configPath, ""
                + "serverUrl=wss://file.example.com/ws/clipboard\n"
                + "groupId=file-group\n"
                + "deviceId=file-device\n"
                + "ed25519PrivateKey=" + Base64.getEncoder().encodeToString(fileKeyPair.getPrivate().getEncoded()) + "\n"
                + "e2eKey=" + Base64.getEncoder().encodeToString(new byte[32]) + "\n");

        ClientConfig config = ClientConfig.fromEnvironment(Map.of(
                "CLIPBOARD_SYNC_CLIENT_CONFIG", configPath.toString(),
                "CLIPBOARD_SYNC_SERVER_URL", "wss://env.example.com/ws/clipboard",
                "CLIPBOARD_SYNC_GROUP_ID", "env-group",
                "CLIPBOARD_SYNC_DEVICE_ID", "env-device",
                "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY", Base64.getEncoder().encodeToString(envKeyPair.getPrivate().getEncoded()),
                "CLIPBOARD_SYNC_E2E_KEY", Base64.getEncoder().encodeToString(new byte[32]),
                "CLIPBOARD_SYNC_CLIPBOARD_POLL_INTERVAL_MILLIS", "250"
        ));

        assertThat(config.serverUri().toString()).isEqualTo("wss://env.example.com/ws/clipboard");
        assertThat(config.groupId()).isEqualTo("env-group");
        assertThat(config.deviceId()).isEqualTo("env-device");
        assertThat(config.clipboardPollInterval().toMillis()).isEqualTo(250);
    }

    @Test
    void rejectsInvalidClipboardPollInterval() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Map<String, String> environment = Map.of(
                "CLIPBOARD_SYNC_SERVER_URL", "wss://relay.example.com/ws/clipboard",
                "CLIPBOARD_SYNC_GROUP_ID", "alice",
                "CLIPBOARD_SYNC_DEVICE_ID", "mac",
                "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                "CLIPBOARD_SYNC_E2E_KEY", Base64.getEncoder().encodeToString(new byte[32]),
                "CLIPBOARD_SYNC_CLIPBOARD_POLL_INTERVAL_MILLIS", "0"
        );

        assertThatThrownBy(() -> ClientConfig.fromEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
