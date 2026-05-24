package com.clipboardsync.client;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Polls a {@link ClipboardService} and reports text changes.
 *
 * <p>The first observed clipboard value is treated as the baseline and is not emitted. This avoids
 * sending a stale clipboard value immediately when the sync process starts.</p>
 */
public class ClipboardWatcher {

    private final ClipboardService clipboardService;
    private final Duration pollInterval;
    private String lastObservedText;
    private boolean initialized;

    /**
     * Creates a clipboard watcher.
     *
     * @param clipboardService clipboard service to poll
     * @param pollInterval delay between polling attempts; must be positive
     */
    public ClipboardWatcher(ClipboardService clipboardService, Duration pollInterval) {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Clipboard poll interval must be positive");
        }
        this.clipboardService = clipboardService;
        this.pollInterval = pollInterval;
    }

    /**
     * Polls once and returns a newly observed text value.
     *
     * @return changed text, or an empty result when there is no new text value
     */
    public Optional<String> poll() {
        Optional<String> current = clipboardService.readText();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        String text = current.get();
        if (!initialized) {
            lastObservedText = text;
            initialized = true;
            return Optional.empty();
        }
        if (Objects.equals(lastObservedText, text)) {
            return Optional.empty();
        }
        lastObservedText = text;
        return Optional.of(text);
    }

    /**
     * Records a text value that should be considered already observed.
     *
     * @param text text value written by the synchronizer
     */
    public void markObserved(String text) {
        lastObservedText = text;
        initialized = true;
    }

    /**
     * Returns the configured polling interval.
     *
     * @return polling interval
     */
    public Duration pollInterval() {
        return pollInterval;
    }
}
