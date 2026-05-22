package com.clipboardsync.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardSyncPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsEmptyDevicePublicKeys() {
        assertThatThrownBy(() -> new ClipboardSyncProperties("/ws/clipboard", new String[]{"*"}, 128, "", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("device-public-keys");
    }

    @Test
    void rejectsBlankPublicKey() {
        assertThatThrownBy(() -> new ClipboardSyncProperties("/ws/clipboard", new String[]{"*"}, 128, "", Map.of("mac", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void acceptsConfiguredDevicePublicKey() {
        assertThatCode(() -> new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                "",
                Map.of("mac", "base64-public-key")
        )).doesNotThrowAnyException();
    }

    @Test
    void loadsDevicePublicKeysFromDirectory() throws Exception {
        Files.writeString(tempDir.resolve("MACBOOK"), "mac-public-key\n");
        Files.writeString(tempDir.resolve("WINDOWS"), "windows-public-key\n");

        ClipboardSyncProperties properties = new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                tempDir.toString(),
                Map.of()
        );

        assertThat(properties.devicePublicKeys())
                .containsEntry("MACBOOK", "mac-public-key")
                .containsEntry("WINDOWS", "windows-public-key");
    }

    @Test
    void configuredPublicKeysOverrideDirectoryPublicKeys() throws Exception {
        Files.writeString(tempDir.resolve("MACBOOK"), "directory-key\n");

        ClipboardSyncProperties properties = new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                tempDir.toString(),
                Map.of("MACBOOK", "configured-key")
        );

        assertThat(properties.devicePublicKeys())
                .containsEntry("MACBOOK", "configured-key");
    }
}
