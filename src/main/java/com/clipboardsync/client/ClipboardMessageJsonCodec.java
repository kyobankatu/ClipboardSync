package com.clipboardsync.client;

import com.clipboardsync.protocol.ClipboardMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serializes and deserializes clipboard relay messages.
 */
class ClipboardMessageJsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * Creates a codec using the relay JSON format.
     */
    ClipboardMessageJsonCodec() {
        this(defaultObjectMapper());
    }

    /**
     * Creates a codec with an explicit object mapper for tests.
     *
     * @param objectMapper JSON mapper
     */
    ClipboardMessageJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a clipboard message.
     *
     * @param message message to serialize
     * @return JSON representation
     * @throws JsonProcessingException if the message cannot be serialized
     */
    String serialize(ClipboardMessage message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }

    /**
     * Deserializes a clipboard message.
     *
     * @param json JSON representation
     * @return parsed clipboard message
     * @throws JsonProcessingException if the JSON is invalid
     */
    ClipboardMessage deserialize(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, ClipboardMessage.class);
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
