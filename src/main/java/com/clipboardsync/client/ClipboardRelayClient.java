package com.clipboardsync.client;

import com.clipboardsync.protocol.ClipboardMessage;

import java.net.http.WebSocket;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Minimal WebSocket client for sending and receiving encrypted clipboard updates.
 */
public class ClipboardRelayClient {

    private final ClientConfig config;
    private final RelayWebSocketTransport transport;
    private final ClipboardMessageJsonCodec jsonCodec;
    private final ClipboardMessageCryptor messageCryptor;

    /**
     * Creates a relay client.
     *
     * @param config client runtime configuration
     */
    public ClipboardRelayClient(ClientConfig config) {
        this(
                config,
                new RelayWebSocketTransport(config, new DeviceHandshakeSigner()),
                new ClipboardMessageJsonCodec(),
                new ClipboardMessageCryptor(config)
        );
    }

    /**
     * Creates a relay client with explicit collaborators for tests.
     *
     * @param config client runtime configuration
     * @param transport WebSocket transport
     * @param jsonCodec clipboard message JSON codec
     * @param messageCryptor clipboard message encryptor/decryptor
     */
    ClipboardRelayClient(
            ClientConfig config,
            RelayWebSocketTransport transport,
            ClipboardMessageJsonCodec jsonCodec,
            ClipboardMessageCryptor messageCryptor
    ) {
        this.config = config;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.messageCryptor = messageCryptor;
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
            ClipboardMessage message = messageCryptor.encryptText(text);
            transport.sendText(webSocket, jsonCodec.serialize(message));
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
        return transport.connect(listener);
    }

    private void handleIncoming(String json, Consumer<String> incomingText) {
        try {
            ClipboardMessage message = jsonCodec.deserialize(json);
            if (Objects.equals(message.sourceDeviceId(), config.deviceId())) {
                return;
            }
            incomingText.accept(messageCryptor.decryptText(message));
        } catch (Exception exception) {
            System.err.println("Rejected incoming clipboard update");
        }
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
