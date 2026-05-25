package com.clipboardsync.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardSyncPropertiesTest {

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
    void keepsConfiguredDevicePublicKeysUnmodified() {
        ClipboardSyncProperties properties = new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                "",
                Map.of("MACBOOK", "mac-public-key")
        );

        assertThat(properties.devicePublicKeys()).containsEntry("MACBOOK", "mac-public-key");
    }

    @Test
    void acceptsEmptyDevicePublicKeysWhenDirectoryOrEnvironmentMayProvideKeys() {
        ClipboardSyncProperties properties = new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                "/config/device-public-keys",
                Map.of()
        );

        assertThat(properties.devicePublicKeys()).isEmpty();
    }
}
