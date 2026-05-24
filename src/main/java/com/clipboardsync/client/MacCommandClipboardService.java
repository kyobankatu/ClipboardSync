package com.clipboardsync.client;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * macOS text clipboard implementation using {@code pbpaste} and {@code pbcopy}.
 *
 * <p>This avoids initializing AWT on macOS, which can cause a Java application icon to appear in
 * the Dock when the sync client is launched in the background.</p>
 */
public class MacCommandClipboardService implements ClipboardService {

    /**
     * Creates a macOS command clipboard service.
     */
    public MacCommandClipboardService() {
    }

    @Override
    public Optional<String> readText() {
        try {
            Process process = processBuilder("pbpaste").start();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode == 0 && !text.isEmpty()) {
                return Optional.of(text);
            }
            return Optional.empty();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    @Override
    public void writeText(String text) {
        try {
            Process process = processBuilder("pbcopy").start();
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("pbcopy failed with exit code " + exitCode);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write macOS clipboard", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing macOS clipboard", exception);
        }
    }

    private ProcessBuilder processBuilder(String command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("LANG", "en_US.UTF-8");
        builder.environment().put("LC_CTYPE", "UTF-8");
        return builder;
    }
}
