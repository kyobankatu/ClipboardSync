package com.clipboardsync.client;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs bidirectional synchronization between the local OS clipboard and the encrypted relay.
 *
 * <p>Remote clipboard writes are tracked and suppressed when the local watcher observes the same
 * value, preventing an update received from another device from being sent back as a new local
 * update.</p>
 */
public class ClipboardSyncRunner {

    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

    private final ClipboardRelayClient relayClient;
    private final ClipboardService clipboardService;
    private final ClipboardWatcher clipboardWatcher;
    private final AtomicReference<String> suppressNextText = new AtomicReference<>();

    /**
     * Creates a sync runner.
     *
     * @param relayClient encrypted relay client
     * @param clipboardService local clipboard service
     * @param clipboardWatcher local clipboard watcher
     */
    public ClipboardSyncRunner(
            ClipboardRelayClient relayClient,
            ClipboardService clipboardService,
            ClipboardWatcher clipboardWatcher
    ) {
        this.relayClient = relayClient;
        this.clipboardService = clipboardService;
        this.clipboardWatcher = clipboardWatcher;
    }

    /**
     * Starts synchronization and blocks until the process is interrupted.
     *
     * <p>If the relay connection closes or fails, the runner waits briefly and reconnects instead
     * of exiting. This keeps login autostart processes alive across network changes, idle tunnel
     * timeouts, and relay restarts.</p>
     */
    public void run() {
        AtomicBoolean stopping = new AtomicBoolean(false);
        AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping.set(true);
            WebSocket webSocket = activeWebSocket.get();
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            }
        }));
        while (!stopping.get()) {
            CountDownLatch closed = new CountDownLatch(1);
            try {
                WebSocket webSocket = relayClient.connect(this::applyRemoteText, closed::countDown);
                activeWebSocket.set(webSocket);
                runUntilDisconnected(webSocket, closed, stopping);
            } catch (Exception exception) {
                System.err.println("Clipboard sync connection failed; retrying");
            } finally {
                activeWebSocket.set(null);
            }
            if (!stopping.get() && !sleep(RECONNECT_DELAY)) {
                stopping.set(true);
            }
        }
    }

    private void runUntilDisconnected(WebSocket webSocket, CountDownLatch closed, AtomicBoolean stopping) {
        while (!stopping.get() && closed.getCount() > 0) {
            try {
                clipboardWatcher.poll().ifPresent(text -> sendIfNotSuppressed(webSocket, text));
            } catch (RuntimeException exception) {
                System.err.println("Clipboard sync send failed; reconnecting");
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect");
                return;
            }
            if (!sleep(clipboardWatcher.pollInterval())) {
                stopping.set(true);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "interrupted");
            }
        }
    }

    private void applyRemoteText(String text) {
        clipboardService.writeText(text);
        suppressNextText.set(text);
        clipboardWatcher.markObserved(text);
    }

    private void sendIfNotSuppressed(WebSocket webSocket, String text) {
        String suppressed = suppressNextText.get();
        if (text.equals(suppressed) && suppressNextText.compareAndSet(suppressed, null)) {
            return;
        }
        if (suppressed != null) {
            suppressNextText.compareAndSet(suppressed, null);
        }
        relayClient.send(webSocket, text);
    }

    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
