package com.clipboardsync.relay;

import com.clipboardsync.config.ClipboardSyncProperties;
import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory relay for encrypted clipboard updates.
 *
 * <p>The service stores only the latest encrypted payload and active WebSocket sessions. It never
 * decrypts clipboard contents.</p>
 */
@Service
public class ClipboardRelayService {

    /** WebSocket session attribute containing the authenticated device identifier. */
    public static final String DEVICE_ID_ATTRIBUTE = "deviceId";

    private final ClipboardSyncProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicReference<ClipboardMessage> latestMessage = new AtomicReference<>();

    /**
     * Creates a relay service.
     *
     * @param properties relay runtime settings
     * @param objectMapper JSON mapper
     */
    public ClipboardRelayService(ClipboardSyncProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers an active client session and replays the latest encrypted update when available.
     *
     * @param deviceId authenticated device identifier
     * @param session WebSocket session
     */
    public void register(String deviceId, WebSocketSession session) {
        sessions.put(deviceId, session);
        ClipboardMessage latest = latestMessage.get();
        if (latest != null && !Objects.equals(latest.sourceDeviceId(), deviceId)) {
            send(session, latest.asLatestStateReplay());
        }
    }

    /**
     * Removes a client session if it is still associated with the provided device.
     *
     * @param deviceId authenticated device identifier
     * @param session WebSocket session being closed
     */
    public void unregister(String deviceId, WebSocketSession session) {
        sessions.remove(deviceId, session);
    }

    /**
     * Accepts and broadcasts an encrypted clipboard update.
     *
     * @param senderDeviceId authenticated device identifier bound to the sender session
     * @param message validated clipboard update
     */
    public void relayUpdate(String senderDeviceId, ClipboardMessage message) {
        validateRelayMessage(senderDeviceId, message);
        latestMessage.set(message);
        sessions.forEach((deviceId, session) -> {
            if (!Objects.equals(deviceId, senderDeviceId) && session.isOpen()) {
                send(session, message);
            }
        });
    }

    /**
     * Validates routing and payload constraints that cannot be expressed with bean validation.
     *
     * @param senderDeviceId authenticated device identifier bound to the sender session
     * @param message clipboard update
     */
    public void validateRelayMessage(String senderDeviceId, ClipboardMessage message) {
        if (message.type() != MessageType.CLIPBOARD_UPDATE) {
            throw new IllegalArgumentException("Only clipboard_update messages can be relayed");
        }
        if (!Objects.equals(senderDeviceId, message.sourceDeviceId())) {
            throw new IllegalArgumentException("sourceDeviceId must match the authenticated device");
        }
        validateBase64ExactSize(message.payload().nonce(), 24, "nonce");
        validateBase64Size(message.payload().ciphertext(), properties.maxCiphertextBytes(), "ciphertext");
    }

    private void validateBase64ExactSize(String value, int expectedBytes, String fieldName) {
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length != expectedBytes) {
            throw new IllegalArgumentException(fieldName + " has invalid size");
        }
    }

    private void validateBase64Size(String value, int maxBytes, String fieldName) {
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length > maxBytes) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum size");
        }
    }

    private void send(WebSocketSession session, ClipboardMessage message) {
        try {
            sendRaw(session, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize clipboard message", exception);
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send WebSocket message", exception);
        }
    }
}
