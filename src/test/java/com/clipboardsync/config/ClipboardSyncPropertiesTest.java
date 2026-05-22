package com.clipboardsync.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardSyncPropertiesTest {

    @Test
    void rejectsEmptyDevicePublicKeys() {
        assertThatThrownBy(() -> new ClipboardSyncProperties("/ws/clipboard", new String[]{"*"}, 128, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("device-public-keys");
    }

    @Test
    void rejectsBlankPublicKey() {
        assertThatThrownBy(() -> new ClipboardSyncProperties("/ws/clipboard", new String[]{"*"}, 128, Map.of("mac", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void acceptsConfiguredDevicePublicKey() {
        assertThatCode(() -> new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                Map.of("mac", "base64-public-key")
        )).doesNotThrowAnyException();
    }
}
