package com.clipboardsync.client;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceHandshakeSignerTest {

    @Test
    void signsHandshakeInputWithEd25519PrivateKey() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        DeviceHandshakeSigner signer = new DeviceHandshakeSigner(
                Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom()
        );

        HandshakeHeaders headers = signer.sign("mac", keyPair.getPrivate(), "/ws/clipboard");
        String signingInput = DeviceHandshakeSigner.signingInput(
                headers.deviceId(),
                headers.timestamp(),
                headers.nonce(),
                "/ws/clipboard"
        );
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(signingInput.getBytes());

        assertThat(headers.deviceId()).isEqualTo("mac");
        assertThat(headers.timestamp()).isEqualTo("2026-05-22T00:00:00Z");
        assertThat(verifier.verify(Base64.getDecoder().decode(headers.signature()))).isTrue();
    }
}
