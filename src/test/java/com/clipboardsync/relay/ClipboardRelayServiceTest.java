package com.clipboardsync.relay;

import com.clipboardsync.config.ClipboardSyncProperties;
import com.clipboardsync.crypto.CryptoAlgorithm;
import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.EncryptedPayload;
import com.clipboardsync.protocol.MessageType;
import com.clipboardsync.protocol.PayloadType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardRelayServiceTest {

    private final ClipboardRelayService relayService = new ClipboardRelayService(
            new ClipboardSyncProperties("/ws/clipboard", new String[]{"*"}, 128, "", Map.of("group-a.device-a", "public-key")),
            new ObjectMapper()
    );

    @Test
    void rejectsMessageFromDifferentAuthenticatedDevice() {
        ClipboardMessage message = message("group-a", "device-a", MessageType.CLIPBOARD_UPDATE);

        assertThatThrownBy(() -> relayService.validateRelayMessage("group-a", "device-b", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceDeviceId");
    }

    @Test
    void rejectsMessageFromDifferentAuthenticatedGroup() {
        ClipboardMessage message = message("group-a", "device-a", MessageType.CLIPBOARD_UPDATE);

        assertThatThrownBy(() -> relayService.validateRelayMessage("group-b", "device-a", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupId");
    }

    @Test
    void rejectsNonUpdateMessage() {
        ClipboardMessage message = message("group-a", "device-a", MessageType.CLIPBOARD_SNAPSHOT);

        assertThatThrownBy(() -> relayService.validateRelayMessage("group-a", "device-a", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clipboard_update");
    }

    @Test
    void rejectsCiphertextAboveConfiguredLimit() {
        String ciphertext = Base64.getEncoder().encodeToString(new byte[129]);
        ClipboardMessage message = new ClipboardMessage(
                MessageType.CLIPBOARD_UPDATE,
                "update-1",
                "group-a",
                "device-a",
                Instant.parse("2026-05-21T00:00:00Z"),
                PayloadType.TEXT,
                new EncryptedPayload(CryptoAlgorithm.XCHACHA20_POLY1305, nonce(), ciphertext)
        );

        assertThatThrownBy(() -> relayService.validateRelayMessage("group-a", "device-a", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ciphertext");
    }

    @Test
    void rejectsNonceWithInvalidSize() {
        ClipboardMessage message = new ClipboardMessage(
                MessageType.CLIPBOARD_UPDATE,
                "update-1",
                "group-a",
                "device-a",
                Instant.parse("2026-05-21T00:00:00Z"),
                PayloadType.TEXT,
                new EncryptedPayload(
                        CryptoAlgorithm.XCHACHA20_POLY1305,
                        Base64.getEncoder().encodeToString(new byte[12]),
                        "Y2lwaGVy"
                )
        );

        assertThatThrownBy(() -> relayService.validateRelayMessage("group-a", "device-a", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonce");
    }

    private static ClipboardMessage message(String groupId, String sourceDeviceId, MessageType type) {
        return new ClipboardMessage(
                type,
                "update-1",
                groupId,
                sourceDeviceId,
                Instant.parse("2026-05-21T00:00:00Z"),
                PayloadType.TEXT,
                new EncryptedPayload(CryptoAlgorithm.XCHACHA20_POLY1305, nonce(), "Y2lwaGVy")
        );
    }

    private static String nonce() {
        return Base64.getEncoder().encodeToString(new byte[24]);
    }
}
