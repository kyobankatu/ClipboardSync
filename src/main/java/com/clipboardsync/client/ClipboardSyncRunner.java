package com.clipboardsync.client;

import java.net.http.WebSocket;
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
     * Starts synchronization and blocks until the WebSocket closes or the process is interrupted.
     *
     * @throws Exception when the initial relay connection cannot be established
     */
    public void run() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        WebSocket webSocket = relayClient.connect(
                this::applyRemoteText,
                () -> {
                    running.set(false);
                    closed.countDown();
                }
        );
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }));
        while (running.get()) {
            clipboardWatcher.poll().ifPresent(text -> sendIfNotSuppressed(webSocket, text));
            if (!sleep()) {
                running.set(false);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "interrupted");
            }
        }
        closed.await();
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

    private boolean sleep() {
        try {
            Thread.sleep(clipboardWatcher.pollInterval().toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
