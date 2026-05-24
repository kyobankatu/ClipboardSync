package com.clipboardsync.client;

import com.clipboardsync.crypto.ClipboardAssociatedData;
import com.clipboardsync.crypto.XChaCha20Poly1305ClipboardCipher;
import com.clipboardsync.protocol.ClipboardMessage;
import com.clipboardsync.protocol.EncryptedPayload;
import com.clipboardsync.protocol.MessageType;
import com.clipboardsync.protocol.PayloadType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Minimal WebSocket client for sending and receiving encrypted clipboard updates.
 */
public class ClipboardRelayClient {

    private final ClientConfig config;
    private final ObjectMapper objectMapper;
    private final DeviceHandshakeSigner handshakeSigner;

    /**
     * Creates a relay client.
     *
     * @param config client runtime configuration
     */
    public ClipboardRelayClient(ClientConfig config) {
        this(config, new DeviceHandshakeSigner(), objectMapper());
    }

    /**
     * Creates a relay client with explicit collaborators for tests.
     *
     * @param config client runtime configuration
     * @param handshakeSigner handshake signer
     * @param objectMapper JSON mapper
     */
    ClipboardRelayClient(ClientConfig config, DeviceHandshakeSigner handshakeSigner, ObjectMapper objectMapper) {
        this.config = config;
        this.handshakeSigner = handshakeSigner;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a single encrypted clipboard update and closes the WebSocket connection.
     *
     * @param text plaintext clipboard text to encrypt locally
     * @throws Exception if connection, encryption, or serialization fails
     */
    public void send(String text) throws Exception {
        WebSocket webSocket = connect(new WebSocket.Listener() {
        });
        send(webSocket, text);
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "sent").join();
    }

    /**
     * Sends a single encrypted clipboard update through an existing WebSocket connection.
     *
     * @param webSocket open WebSocket connection
     * @param text plaintext clipboard text to encrypt locally
     */
    public void send(WebSocket webSocket, String text) {
        try {
            ClipboardMessage message = encryptedMessage(text);
            webSocket.sendText(objectMapper.writeValueAsString(message), true).join();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to send encrypted clipboard update", exception);
        }
    }

    /**
     * Listens for encrypted clipboard updates and prints decrypted text to stdout.
     *
     * @throws Exception if the initial connection fails
     */
    public void listen() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        connect(System.out::println, closed::countDown);
        closed.await();
    }

    /**
     * Opens a relay WebSocket connection and decrypts incoming updates for the provided callback.
     *
     * @param incomingText receives plaintext from other devices after local decryption
     * @param closedCallback invoked when the WebSocket closes or fails
     * @return open WebSocket connection
     * @throws Exception if the initial connection fails
     */
    public WebSocket connect(Consumer<String> incomingText, Runnable closedCallback) throws Exception {
        return connect(new DecryptingListener(incomingText, closedCallback));
    }

    private WebSocket connect(WebSocket.Listener listener) throws Exception {
        HandshakeHeaders headers = handshakeSigner.sign(
                config.groupId(),
                config.deviceId(),
                config.ed25519PrivateKey(),
                config.websocketPath()
        );
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("X-Clipboard-Group-Id", headers.groupId())
                .header("X-Clipboard-Device-Id", headers.deviceId())
                .header("X-Clipboard-Timestamp", headers.timestamp())
                .header("X-Clipboard-Nonce", headers.nonce())
                .header("X-Clipboard-Signature", headers.signature())
                .buildAsync(config.serverUri(), listener)
                .join();
    }

    private ClipboardMessage encryptedMessage(String text) throws Exception {
        String updateId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        byte[] associatedData = ClipboardAssociatedData.fromMetadata(
                MessageType.CLIPBOARD_UPDATE,
                updateId,
                config.groupId(),
                config.deviceId(),
                createdAt,
                PayloadType.TEXT
        );
        EncryptedPayload payload = new XChaCha20Poly1305ClipboardCipher(config.e2eKey())
                .encryptText(text, associatedData);
        return new ClipboardMessage(
                MessageType.CLIPBOARD_UPDATE,
                updateId,
                config.groupId(),
                config.deviceId(),
                createdAt,
                PayloadType.TEXT,
                payload
        );
    }

    private void handleIncoming(String json, Consumer<String> incomingText) {
        try {
            ClipboardMessage message = objectMapper.readValue(json, ClipboardMessage.class);
            if (Objects.equals(message.sourceDeviceId(), config.deviceId())) {
                return;
            }
            String plaintext = new XChaCha20Poly1305ClipboardCipher(config.e2eKey())
                    .decryptText(message.payload(), ClipboardAssociatedData.fromMessageMetadata(message));
            incomingText.accept(plaintext);
        } catch (Exception exception) {
            System.err.println("Rejected incoming clipboard update");
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private class DecryptingListener implements WebSocket.Listener {

        private final Consumer<String> incomingText;
        private final Runnable closedCallback;
        private final StringBuilder buffer = new StringBuilder();

        private DecryptingListener(Consumer<String> incomingText, Runnable closedCallback) {
            this.incomingText = incomingText;
            this.closedCallback = closedCallback;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                handleIncoming(buffer.toString(), incomingText);
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closedCallback.run();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
            closedCallback.run();
        }
    }
}
