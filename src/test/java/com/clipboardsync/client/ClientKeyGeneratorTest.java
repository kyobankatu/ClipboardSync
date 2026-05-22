package com.clipboardsync.client;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyGeneratorTest {

    @Test
    void generatesUsableEd25519KeyPairAndE2eKey() throws Exception {
        GeneratedClientKeys keys = ClientKeyGenerator.generate();
        PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keys.ed25519PrivateKey())));
        PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keys.ed25519PublicKey())));

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update("message".getBytes());
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update("message".getBytes());

        assertThat(verifier.verify(signature)).isTrue();
        assertThat(Base64.getDecoder().decode(keys.e2eKey()))
                .hasSize(32);
    }
}
