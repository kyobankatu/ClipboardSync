package com.clipboardsync.crypto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Authenticated encryption algorithms supported by ClipboardSync clients.
 */
public enum CryptoAlgorithm {
    /** XChaCha20 stream cipher with Poly1305 authentication tag. */
    XCHACHA20_POLY1305("XChaCha20-Poly1305");

    private final String wireName;

    CryptoAlgorithm(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Parses a wire value into an algorithm.
     *
     * @param value JSON string value
     * @return matching algorithm
     */
    @JsonCreator
    public static CryptoAlgorithm fromWireName(String value) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown crypto algorithm"));
    }

    /**
     * Returns the JSON string value.
     *
     * @return wire representation
     */
    @JsonValue
    public String wireName() {
        return wireName;
    }
}
