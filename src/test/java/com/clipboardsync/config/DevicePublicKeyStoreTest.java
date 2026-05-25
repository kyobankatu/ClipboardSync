package com.clipboardsync.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevicePublicKeyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsEmptyDevicePublicKeys() {
        assertThatThrownBy(() -> new DevicePublicKeyStore(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("device-public-keys");
    }

    @Test
    void normalizesConfiguredDevicePublicKey() {
        DevicePublicKeyStore store = new DevicePublicKeyStore(Map.of("MACBOOK", "mac-public-key"));

        assertThat(store.devicePublicKeys()).containsEntry("default/MACBOOK", "mac-public-key");
        assertThat(store.publicKeyFor("default", "MACBOOK")).contains("mac-public-key");
    }

    @Test
    void loadsDevicePublicKeysFromDirectory() throws Exception {
        Files.writeString(tempDir.resolve("MACBOOK"), "mac-public-key\n");
        Files.writeString(tempDir.resolve("WINDOWS"), "windows-public-key\n");

        DevicePublicKeyStore store = new DevicePublicKeyStore(properties(tempDir, Map.of()));

        assertThat(store.devicePublicKeys())
                .containsEntry("default/MACBOOK", "mac-public-key")
                .containsEntry("default/WINDOWS", "windows-public-key");
        assertThat(store.publicKeyFor("default", "MACBOOK")).contains("mac-public-key");
    }

    @Test
    void loadsGroupedDevicePublicKeysFromDirectory() throws Exception {
        Files.writeString(tempDir.resolve("alice.MACBOOK"), "alice-mac-public-key\n");
        Files.createDirectories(tempDir.resolve("bob"));
        Files.writeString(tempDir.resolve("bob").resolve("MACBOOK"), "bob-mac-public-key\n");

        DevicePublicKeyStore store = new DevicePublicKeyStore(properties(tempDir, Map.of()));

        assertThat(store.publicKeyFor("alice", "MACBOOK")).contains("alice-mac-public-key");
        assertThat(store.publicKeyFor("bob", "MACBOOK")).contains("bob-mac-public-key");
    }

    @Test
    void configuredPublicKeysOverrideDirectoryPublicKeys() throws Exception {
        Files.writeString(tempDir.resolve("MACBOOK"), "directory-key\n");

        DevicePublicKeyStore store = new DevicePublicKeyStore(properties(tempDir, Map.of("MACBOOK", "configured-key")));

        assertThat(store.devicePublicKeys()).containsEntry("default/MACBOOK", "configured-key");
    }

    private static ClipboardSyncProperties properties(Path directory, Map<String, String> devicePublicKeys) {
        return new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                directory.toString(),
                devicePublicKeys
        );
    }
}
