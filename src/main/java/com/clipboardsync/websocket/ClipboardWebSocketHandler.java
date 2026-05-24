package com.clipboardsync.websocket;

import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.ErrorMessage;
import com.clipboardsync.relay.ClipboardRelayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;

/**
 * Handles encrypted clipboard updates from connected clients.
 */
@Component
public class ClipboardWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ClipboardRelayService relayService;

    /**
     * Creates the WebSocket handler.
     *
     * @param objectMapper JSON mapper
     * @param validator bean validator
     * @param relayService encrypted payload relay
     */
    public ClipboardWebSocketHandler(
            ObjectMapper objectMapper,
            Validator validator,
            ClipboardRelayService relayService
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.relayService = relayService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        relayService.register(groupId(session), deviceId(session), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            ClipboardMessage message = objectMapper.readValue(textMessage.getPayload(), ClipboardMessage.class);
            validate(message);
            relayService.relayUpdate(groupId(session), deviceId(session), message);
        } catch (Exception exception) {
            sendError(session, "invalid_message", "Clipboard message was rejected");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        relayService.unregister(groupId(session), deviceId(session), session);
    }

    private void validate(ClipboardMessage message) {
        Set<ConstraintViolation<ClipboardMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Clipboard message failed validation");
        }
    }

    private String deviceId(WebSocketSession session) {
        Object deviceId = session.getAttributes().get(ClipboardRelayService.DEVICE_ID_ATTRIBUTE);
        if (deviceId instanceof String value) {
            return value;
        }
        throw new IllegalStateException("Missing authenticated device identifier");
    }

    private String groupId(WebSocketSession session) {
        Object groupId = session.getAttributes().get(ClipboardRelayService.GROUP_ID_ATTRIBUTE);
        if (groupId instanceof String value) {
            return value;
        }
        throw new IllegalStateException("Missing authenticated group identifier");
    }

    private void sendError(WebSocketSession session, String code, String message) {
        try {
            String json = objectMapper.writeValueAsString(ErrorMessage.of(code, message));
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException ignored) {
            // Nothing else can be sent if the session is already broken.
        }
    }
}
