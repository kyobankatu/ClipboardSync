package com.clipboardsync.crypto;

import com.clipboardsync.protocol.EncryptedPayload;
import com.google.crypto.tink.subtle.XChaCha20Poly1305;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Client-side clipboard cipher backed by XChaCha20-Poly1305.
 *
 * <p>This class is intended for clients and test utilities. The relay server should not use it to
 * decrypt clipboard contents.</p>
 */
public final class XChaCha20Poly1305ClipboardCipher {

    /** XChaCha20-Poly1305 raw key size in bytes. */
    public static final int KEY_SIZE_BYTES = 32;

    /** XChaCha20 nonce size in bytes. */
    public static final int NONCE_SIZE_BYTES = 24;

    private final byte[] key;

    /**
     * Creates a cipher with a raw 32-byte symmetric key.
     *
     * @param key raw symmetric key; the value is copied and must not be logged
     */
    public XChaCha20Poly1305ClipboardCipher(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("XChaCha20-Poly1305 key must be 32 bytes");
        }
        this.key = key.clone();
    }

    /**
     * Encrypts UTF-8 text for transport through the relay server.
     *
     * @param plaintext clipboard text
     * @param associatedData authenticated metadata bytes
     * @return encrypted payload with Base64 encoded nonce and ciphertext
     * @throws GeneralSecurityException if encryption fails
     */
    public EncryptedPayload encryptText(String plaintext, byte[] associatedData) throws GeneralSecurityException {
        byte[] plaintextBytes = Objects.requireNonNull(plaintext, "plaintext")
                .getBytes(StandardCharsets.UTF_8);
        byte[] combined = primitive().encrypt(plaintextBytes, safeAssociatedData(associatedData));
        byte[] nonce = Arrays.copyOfRange(combined, 0, NONCE_SIZE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, NONCE_SIZE_BYTES, combined.length);
        Base64.Encoder encoder = Base64.getEncoder();
        return new EncryptedPayload(
                CryptoAlgorithm.XCHACHA20_POLY1305,
                encoder.encodeToString(nonce),
                encoder.encodeToString(ciphertext)
        );
    }

    /**
     * Decrypts a received encrypted payload into UTF-8 text.
     *
     * @param payload encrypted payload from the relay
     * @param associatedData authenticated metadata bytes
     * @return decrypted clipboard text
     * @throws GeneralSecurityException if authentication or decryption fails
     */
    public String decryptText(EncryptedPayload payload, byte[] associatedData) throws GeneralSecurityException {
        Objects.requireNonNull(payload, "payload");
        if (payload.algorithm() != CryptoAlgorithm.XCHACHA20_POLY1305) {
            throw new GeneralSecurityException("Unsupported crypto algorithm");
        }
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] nonce = decoder.decode(payload.nonce());
        byte[] ciphertext = decoder.decode(payload.ciphertext());
        if (nonce.length != NONCE_SIZE_BYTES) {
            throw new GeneralSecurityException("Invalid XChaCha20 nonce size");
        }
        byte[] combined = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
        byte[] plaintext = primitive().decrypt(combined, safeAssociatedData(associatedData));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private XChaCha20Poly1305 primitive() throws GeneralSecurityException {
        return new XChaCha20Poly1305(key);
    }

    private static byte[] safeAssociatedData(byte[] associatedData) {
        return associatedData == null ? new byte[0] : associatedData;
    }
}
