package com.clipboardsync.config;

import com.clipboardsync.websocket.ClipboardAuthHandshakeInterceptor;
import com.clipboardsync.websocket.ClipboardWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers WebSocket endpoints used by clipboard clients.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClipboardSyncProperties properties;
    private final ClipboardWebSocketHandler handler;
    private final ClipboardAuthHandshakeInterceptor interceptor;

    /**
     * Creates the WebSocket configuration.
     *
     * @param properties relay runtime settings
     * @param handler WebSocket message handler
     * @param interceptor handshake authentication and device binding interceptor
     */
    public WebSocketConfig(
            ClipboardSyncProperties properties,
            ClipboardWebSocketHandler handler,
            ClipboardAuthHandshakeInterceptor interceptor
    ) {
        this.properties = properties;
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.websocketPath())
                .addInterceptors(interceptor)
                .setAllowedOrigins(properties.allowedOrigins());
    }
}
