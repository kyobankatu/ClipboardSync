package com.clipboardsync.client.autostart;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Selects the current operating system's autostart implementation.
 */
public final class AutostartServiceFactory {

    private AutostartServiceFactory() {
    }

    /**
     * Creates an autostart service for the current operating system.
     *
     * @param jarPath packaged jar to start at login
     * @return platform autostart service
     */
    public static AutostartService create(Path jarPath) {
        RuntimeCommandResolver commandResolver = new RuntimeCommandResolver(jarPath);
        ProcessRunner processRunner = new ProcessRunner();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return new MacLaunchAgentAutostartService(commandResolver, processRunner);
        }
        if (os.contains("win")) {
            return new WindowsTaskSchedulerAutostartService(commandResolver, processRunner, System.getenv());
        }
        throw new IllegalStateException("Autostart is currently supported only on macOS and Windows");
    }
}
