package com.clipboardsync.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClipboardSyncPropertiesEnvironmentTest {

    @Test
    void keepsConfiguredDevicePublicKeys() {
        ClipboardSyncProperties properties = new ClipboardSyncProperties(
                "/ws/clipboard",
                new String[]{"*"},
                128,
                Map.of("MACBOOK", "public-key")
        );

        assertThat(properties.devicePublicKeys())
                .containsEntry("MACBOOK", "public-key");
    }
}
