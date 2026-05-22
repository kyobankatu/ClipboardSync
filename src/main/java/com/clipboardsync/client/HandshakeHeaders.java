package com.clipboardsync.client;

/**
 * Headers required by the relay server's Ed25519 WebSocket handshake authentication.
 *
 * @param deviceId authenticated device identifier
 * @param timestamp ISO-8601 timestamp used in the signature
 * @param nonce one-time random value used in the signature
 * @param signature Base64 encoded Ed25519 signature
 */
public record HandshakeHeaders(
        String deviceId,
        String timestamp,
        String nonce,
        String signature
) {
}
