package com.clipboardsync.crypto;

import com.clipboardsync.protocol.ClipboardMessage;

import java.nio.charset.StandardCharsets;

/**
 * Builds deterministic additional authenticated data for clipboard encryption.
 */
public final class ClipboardAssociatedData {

    private ClipboardAssociatedData() {
    }

    /**
     * Creates associated data from message metadata that must not be tampered with in transit.
     *
     * @param message clipboard message metadata
     * @return UTF-8 encoded associated data
     */
    public static byte[] fromMessageMetadata(ClipboardMessage message) {
        String value = "v1\n"
                + "type=" + message.type().wireName() + "\n"
                + "updateId=" + message.updateId() + "\n"
                + "sourceDeviceId=" + message.sourceDeviceId() + "\n"
                + "createdAt=" + message.createdAt() + "\n"
                + "payloadType=" + message.payloadType().wireName() + "\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
