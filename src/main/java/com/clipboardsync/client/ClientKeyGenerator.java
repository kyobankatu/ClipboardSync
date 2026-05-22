package com.clipboardsync.client;

import com.clipboardsync.crypto.XChaCha20Poly1305ClipboardCipher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates authentication and E2E encryption keys for development clients.
 */
public final class ClientKeyGenerator {

    private ClientKeyGenerator() {
    }

    /**
     * Generates a new Ed25519 key pair and XChaCha20-Poly1305 key.
     *
     * @return generated Base64 encoded key material
     * @throws Exception if key generation fails
     */
    public static GeneratedClientKeys generate() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] e2eKey = new byte[XChaCha20Poly1305ClipboardCipher.KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(e2eKey);
        Base64.Encoder encoder = Base64.getEncoder();
        return new GeneratedClientKeys(
                encoder.encodeToString(keyPair.getPrivate().getEncoded()),
                encoder.encodeToString(keyPair.getPublic().getEncoded()),
                encoder.encodeToString(e2eKey)
        );
    }
}
