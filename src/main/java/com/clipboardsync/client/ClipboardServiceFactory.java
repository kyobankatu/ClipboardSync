package com.clipboardsync.client;

import java.util.Locale;

/**
 * Selects the best clipboard implementation for the current operating system.
 */
public final class ClipboardServiceFactory {

    private ClipboardServiceFactory() {
    }

    /**
     * Creates a clipboard service for the current operating system.
     *
     * @return platform clipboard service
     */
    public static ClipboardService create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return new MacCommandClipboardService();
        }
        return new AwtClipboardService();
    }
}
