package com.clipboardsync.protocol;

import com.clipboardsync.crypto.CryptoAlgorithm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Encrypted clipboard payload transported by the relay.
 *
 * <p>{@code nonce} and {@code ciphertext} are Base64 encoded. The plaintext is never sent to the
 * server and must not be logged by clients.</p>
 *
 * @param algorithm encryption algorithm used by the sending client
 * @param nonce Base64 encoded 24-byte XChaCha20 nonce
 * @param ciphertext Base64 encoded ciphertext and authentication tag
 */
public record EncryptedPayload(
        @NotNull CryptoAlgorithm algorithm,
        @NotBlank String nonce,
        @NotBlank String ciphertext
) {
}
