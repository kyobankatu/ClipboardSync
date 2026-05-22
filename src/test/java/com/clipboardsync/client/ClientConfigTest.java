package com.clipboardsync.client;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientConfigTest {

    @Test
    void loadsRequiredValuesFromEnvironment() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Map<String, String> environment = Map.of(
                "CLIPBOARD_SYNC_SERVER_URL", "wss://relay.example.com/ws/clipboard",
                "CLIPBOARD_SYNC_DEVICE_ID", "mac",
                "CLIPBOARD_SYNC_ED25519_PRIVATE_KEY", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                "CLIPBOARD_SYNC_E2E_KEY", Base64.getEncoder().encodeToString(new byte[32])
        );

        ClientConfig config = ClientConfig.fromEnvironment(environment);

        assertThat(config.serverUri().toString()).isEqualTo("wss://relay.example.com/ws/clipboard");
        assertThat(config.websocketPath()).isEqualTo("/ws/clipboard");
        assertThat(config.deviceId()).isEqualTo("mac");
        assertThat(config.e2eKey()).hasSize(32);
    }

    @Test
    void rejectsMissingRequiredValue() {
        assertThatThrownBy(() -> ClientConfig.fromEnvironment(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIPBOARD_SYNC_SERVER_URL");
    }
}
