package com.clipboardsync.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Type of clipboard payload encrypted by the sending client.
 */
public enum PayloadType {
    /** UTF-8 encoded plain text before client-side encryption. */
    TEXT("text");

    private final String wireName;

    PayloadType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Parses a wire value into a payload type.
     *
     * @param value JSON string value
     * @return matching payload type
     */
    @JsonCreator
    public static PayloadType fromWireName(String value) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payload type"));
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
