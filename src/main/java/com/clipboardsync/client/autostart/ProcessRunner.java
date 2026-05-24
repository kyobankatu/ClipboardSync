package com.clipboardsync.client.autostart;

import java.io.IOException;
import java.util.List;

/**
 * Runs operating system commands for autostart registration.
 */
public class ProcessRunner {

    /**
     * Creates a process runner.
     */
    public ProcessRunner() {
    }

    /**
     * Runs a command and requires a zero exit status.
     *
     * @param command command arguments
     * @throws IOException if the command cannot be started or exits with a non-zero status
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }
}
