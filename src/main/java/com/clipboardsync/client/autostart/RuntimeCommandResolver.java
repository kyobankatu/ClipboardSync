package com.clipboardsync.client.autostart;

import com.clipboardsync.ClipboardSyncApplication;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the stable command that should be registered for login autostart.
 */
public class RuntimeCommandResolver {

    /**
     * Creates a command resolver.
     */
    public RuntimeCommandResolver() {
    }

    /**
     * Resolves a command that starts {@code client sync}.
     *
     * @return autostart command
     */
    public AutostartCommand resolve() {
        Path jarPath = currentJarPath();
        Path javaPath = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        return new AutostartCommand(List.of(
                javaPath.toAbsolutePath().toString(),
                "-jar",
                jarPath.toAbsolutePath().toString(),
                "client",
                "sync"
        ));
    }

    private Path currentJarPath() {
        try {
            URI location = ClipboardSyncApplication.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path path = Path.of(location);
            if (path.toString().endsWith(".jar")) {
                return path;
            }
            throw new IllegalStateException("install-autostart must be run from a packaged jar");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to resolve packaged application path", exception);
        }
    }

    private static String executable(String name) {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? name + ".exe" : name;
    }
}
