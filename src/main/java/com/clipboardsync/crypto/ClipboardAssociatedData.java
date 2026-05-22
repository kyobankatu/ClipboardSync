package com.clipboardsync.crypto;

import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.MessageType;
import com.clipboardsync.protocol.PayloadType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
        return fromMetadata(
                message.type(),
                message.updateId(),
                message.sourceDeviceId(),
                message.createdAt(),
                message.payloadType()
        );
    }

    /**
     * Creates associated data from individual metadata fields before the encrypted payload exists.
     *
     * @param type message type
     * @param updateId unique update identifier
     * @param sourceDeviceId sender device identifier
     * @param createdAt sender-side creation timestamp
     * @param payloadType clipboard payload type before encryption
     * @return UTF-8 encoded associated data
     */
    public static byte[] fromMetadata(
            MessageType type,
            String updateId,
            String sourceDeviceId,
            Instant createdAt,
            PayloadType payloadType
    ) {
        String value = "v1\n"
                + "type=" + type.wireName() + "\n"
                + "updateId=" + updateId + "\n"
                + "sourceDeviceId=" + sourceDeviceId + "\n"
                + "createdAt=" + createdAt + "\n"
                + "payloadType=" + payloadType.wireName() + "\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
