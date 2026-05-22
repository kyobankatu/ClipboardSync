package com.clipboardsync.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Wire-level message types exchanged between clients and the relay server.
 */
public enum MessageType {
    /** Initial client greeting reserved for protocol evolution. */
    HELLO("hello"),
    /** Client-to-server clipboard update containing an encrypted payload. */
    CLIPBOARD_UPDATE("clipboard_update"),
    /** Reserved for protocol evolution; the initial server replays updates without rewriting type. */
    CLIPBOARD_SNAPSHOT("clipboard_snapshot"),
    /** Server-to-client protocol or validation error. */
    ERROR("error");

    private final String wireName;

    MessageType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Parses a wire value into a message type.
     *
     * @param value JSON string value
     * @return matching message type
     */
    @JsonCreator
    public static MessageType fromWireName(String value) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type"));
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
