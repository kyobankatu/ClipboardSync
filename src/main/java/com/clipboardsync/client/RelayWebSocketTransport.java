package com.clipboardsync.client;

import java.net.http.HttpClient;
import java.net.http.WebSocket;

/**
 * Opens authenticated relay WebSocket connections and sends raw JSON frames.
 */
class RelayWebSocketTransport {

    private final ClientConfig config;
    private final DeviceHandshakeSigner handshakeSigner;
    private final HttpClient httpClient;

    /**
     * Creates a transport using the default HTTP client.
     *
     * @param config client runtime configuration
     * @param handshakeSigner handshake signer
     */
    RelayWebSocketTransport(ClientConfig config, DeviceHandshakeSigner handshakeSigner) {
        this(config, handshakeSigner, HttpClient.newHttpClient());
    }

    /**
     * Creates a transport with an explicit HTTP client for tests.
     *
     * @param config client runtime configuration
     * @param handshakeSigner handshake signer
     * @param httpClient HTTP client used to open WebSocket connections
     */
    RelayWebSocketTransport(ClientConfig config, DeviceHandshakeSigner handshakeSigner, HttpClient httpClient) {
        this.config = config;
        this.handshakeSigner = handshakeSigner;
        this.httpClient = httpClient;
    }

    /**
     * Opens a WebSocket connection with signed device authentication headers.
     *
     * @param listener WebSocket listener
     * @return connected WebSocket
     * @throws Exception if signing or connection setup fails
     */
    WebSocket connect(WebSocket.Listener listener) throws Exception {
        HandshakeHeaders headers = handshakeSigner.sign(
                config.groupId(),
                config.deviceId(),
                config.ed25519PrivateKey(),
                config.websocketPath()
        );
        return httpClient.newWebSocketBuilder()
                .header("X-Clipboard-Group-Id", headers.groupId())
                .header("X-Clipboard-Device-Id", headers.deviceId())
                .header("X-Clipboard-Timestamp", headers.timestamp())
                .header("X-Clipboard-Nonce", headers.nonce())
                .header("X-Clipboard-Signature", headers.signature())
                .buildAsync(config.serverUri(), listener)
                .join();
    }

    /**
     * Sends a complete text frame.
     *
     * @param webSocket open WebSocket
     * @param json serialized clipboard message
     */
    void sendText(WebSocket webSocket, String json) {
        webSocket.sendText(json, true).join();
    }
}
