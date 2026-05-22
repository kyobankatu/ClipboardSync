package com.clipboardsync.client;

/**
 * Base64 encoded keys generated for a development client setup.
 *
 * @param ed25519PrivateKey private key that must stay only on the client device
 * @param ed25519PublicKey public key that can be registered on the relay server
 * @param e2eKey XChaCha20-Poly1305 key shared only by trusted client devices
 */
public record GeneratedClientKeys(
        String ed25519PrivateKey,
        String ed25519PublicKey,
        String e2eKey
) {
}
