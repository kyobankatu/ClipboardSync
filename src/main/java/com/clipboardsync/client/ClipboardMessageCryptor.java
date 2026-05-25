package com.clipboardsync.client;

import com.clipboardsync.crypto.ClipboardAssociatedData;
import com.clipboardsync.crypto.XChaCha20Poly1305ClipboardCipher;
import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.EncryptedPayload;
import com.clipboardsync.protocol.MessageType;
import com.clipboardsync.protocol.PayloadType;

import java.time.Instant;
import java.util.UUID;

/**
 * Encrypts outbound clipboard text and decrypts inbound relay messages.
 */
class ClipboardMessageCryptor {

    private final ClientConfig config;
    private final XChaCha20Poly1305ClipboardCipher cipher;

    /**
     * Creates a message cryptor from client configuration.
     *
     * @param config client runtime configuration
     */
    ClipboardMessageCryptor(ClientConfig config) {
        this.config = config;
        this.cipher = new XChaCha20Poly1305ClipboardCipher(config.e2eKey());
    }

    /**
     * Creates an encrypted text clipboard update.
     *
     * @param text plaintext clipboard text
     * @return encrypted relay message
     * @throws Exception if encryption fails
     */
    ClipboardMessage encryptText(String text) throws Exception {
        String updateId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        byte[] associatedData = ClipboardAssociatedData.fromMetadata(
                MessageType.CLIPBOARD_UPDATE,
                updateId,
                config.groupId(),
                config.deviceId(),
                createdAt,
                PayloadType.TEXT
        );
        EncryptedPayload payload = cipher.encryptText(text, associatedData);
        return new ClipboardMessage(
                MessageType.CLIPBOARD_UPDATE,
                updateId,
                config.groupId(),
                config.deviceId(),
                createdAt,
                PayloadType.TEXT,
                payload
        );
    }

    /**
     * Decrypts an encrypted relay message as text.
     *
     * @param message encrypted relay message
     * @return plaintext clipboard text
     * @throws Exception if authentication or decryption fails
     */
    String decryptText(ClipboardMessage message) throws Exception {
        return cipher.decryptText(message.payload(), ClipboardAssociatedData.fromMessageMetadata(message));
    }
}
