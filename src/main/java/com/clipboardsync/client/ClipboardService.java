package com.clipboardsync.client;

import java.util.Optional;

/**
 * Reads and writes the local operating system text clipboard.
 *
 * <p>This abstraction keeps native clipboard access separate from relay and encryption logic so
 * synchronization behavior can be tested without touching the real desktop clipboard.</p>
 */
public interface ClipboardService {

    /**
     * Reads the current text clipboard value.
     *
     * @return current clipboard text, or an empty result when the clipboard has no text value
     */
    Optional<String> readText();

    /**
     * Writes text into the local clipboard.
     *
     * @param text plaintext text to store in the local clipboard
     */
    void writeText(String text);
}
