package com.clipboardsync.client;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Creates signed WebSocket handshake headers for a registered client device.
 */
public class DeviceHandshakeSigner {

    private static final int NONCE_BYTES = 24;

    private final Clock clock;
    private final SecureRandom secureRandom;

    /**
     * Creates a signer using the system UTC clock.
     */
    public DeviceHandshakeSigner() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    /**
     * Creates a signer with explicit time and randomness sources for tests.
     *
     * @param clock current time source
     * @param secureRandom randomness source for handshake nonces
     */
    DeviceHandshakeSigner(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Signs the relay handshake metadata.
     *
     * @param groupId synchronization group identifier
     * @param deviceId stable local device identifier within the group
     * @param privateKey Ed25519 private key for this device
     * @param websocketPath path used by the WebSocket endpoint
     * @return signed handshake headers
     * @throws Exception if signing fails
     */
    public HandshakeHeaders sign(
            String groupId,
            String deviceId,
            PrivateKey privateKey,
            String websocketPath
    ) throws Exception {
        String timestamp = Instant.now(clock).toString();
        String nonce = nonce();
        String signingInput = signingInput(groupId, deviceId, timestamp, nonce, websocketPath);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        return new HandshakeHeaders(groupId, deviceId, timestamp, nonce, signature);
    }

    /**
     * Builds the exact signing input verified by the relay server.
     *
     * @param groupId synchronization group identifier
     * @param deviceId stable local device identifier within the group
     * @param timestamp ISO-8601 timestamp
     * @param nonce one-time random value
     * @param websocketPath WebSocket path
     * @return deterministic signing input
     */
    public static String signingInput(
            String groupId,
            String deviceId,
            String timestamp,
            String nonce,
            String websocketPath
    ) {
        return "v1\n"
                + "groupId=" + groupId + "\n"
                + "deviceId=" + deviceId + "\n"
                + "timestamp=" + timestamp + "\n"
                + "nonce=" + nonce + "\n"
                + "path=" + websocketPath + "\n";
    }

    private String nonce() {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }
}
