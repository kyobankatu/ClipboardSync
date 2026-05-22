package com.clipboardsync.crypto;

import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.EncryptedPayload;
import com.clipboardsync.protocol.MessageType;
import com.clipboardsync.protocol.PayloadType;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XChaCha20Poly1305ClipboardCipherTest {

    @Test
    void encryptsAndDecryptsText() throws Exception {
        XChaCha20Poly1305ClipboardCipher cipher = new XChaCha20Poly1305ClipboardCipher(testKey((byte) 7));
        byte[] aad = "metadata".getBytes();

        EncryptedPayload encrypted = cipher.encryptText("hello clipboard", aad);

        assertThat(cipher.decryptText(encrypted, aad)).isEqualTo("hello clipboard");
    }

    @Test
    void usesDifferentNonceForEachEncryption() throws Exception {
        XChaCha20Poly1305ClipboardCipher cipher = new XChaCha20Poly1305ClipboardCipher(testKey((byte) 9));
        byte[] aad = "metadata".getBytes();

        EncryptedPayload first = cipher.encryptText("same text", aad);
        EncryptedPayload second = cipher.encryptText("same text", aad);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
    }

    @Test
    void rejectsModifiedAssociatedData() throws Exception {
        XChaCha20Poly1305ClipboardCipher cipher = new XChaCha20Poly1305ClipboardCipher(testKey((byte) 11));
        EncryptedPayload encrypted = cipher.encryptText("secret", "metadata".getBytes());

        assertThatThrownBy(() -> cipher.decryptText(encrypted, "modified".getBytes()))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void buildsStableAssociatedDataFromMessageMetadata() {
        ClipboardMessage message = new ClipboardMessage(
                MessageType.CLIPBOARD_UPDATE,
                "update-1",
                "device-a",
                Instant.parse("2026-05-21T00:00:00Z"),
                PayloadType.TEXT,
                new EncryptedPayload(CryptoAlgorithm.XCHACHA20_POLY1305, "bm9uY2U=", "Y2lwaGVy")
        );

        assertThat(ClipboardAssociatedData.fromMessageMetadata(message))
                .asString()
                .contains("type=clipboard_update")
                .contains("updateId=update-1")
                .contains("sourceDeviceId=device-a");
    }

    private static byte[] testKey(byte value) {
        byte[] key = new byte[XChaCha20Poly1305ClipboardCipher.KEY_SIZE_BYTES];
        for (int index = 0; index < key.length; index++) {
            key[index] = value;
        }
        return key;
    }
}
