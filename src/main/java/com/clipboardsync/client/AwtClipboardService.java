package com.clipboardsync.client;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * Text clipboard implementation backed by the Java AWT system clipboard.
 *
 * <p>This implementation is intended for desktop sessions on macOS and Windows. It rejects
 * headless environments because SSH-only or server sessions usually do not have a usable
 * operating system clipboard.</p>
 */
public class AwtClipboardService implements ClipboardService {

    /**
     * Creates an AWT clipboard service.
     *
     * @throws IllegalStateException when the current JVM is running without desktop clipboard support
     */
    public AwtClipboardService() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("OS clipboard sync requires a non-headless desktop session");
        }
    }

    @Override
    public Optional<String> readText() {
        java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            return Optional.empty();
        }
        try {
            Object value = clipboard.getData(DataFlavor.stringFlavor);
            if (value instanceof String text && !text.isEmpty()) {
                return Optional.of(text);
            }
            return Optional.empty();
        } catch (UnsupportedFlavorException | IOException | IllegalStateException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void writeText(String text) {
        java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }
}
