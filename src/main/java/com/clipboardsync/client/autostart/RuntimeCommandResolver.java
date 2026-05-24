package com.clipboardsync.client.autostart;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the stable command that should be registered for login autostart.
 */
public class RuntimeCommandResolver {

    private final Path jarPath;

    /**
     * Creates a command resolver.
     *
     * @param jarPath packaged jar to start at login
     */
    public RuntimeCommandResolver(Path jarPath) {
        if (jarPath == null || !jarPath.toString().endsWith(".jar")) {
            throw new IllegalArgumentException("Autostart jar path must point to a .jar file");
        }
        this.jarPath = jarPath;
    }

    /**
     * Resolves a command that starts {@code client sync}.
     *
     * @return autostart command
     */
    public AutostartCommand resolve() {
        Path javaPath = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        return new AutostartCommand(List.of(
                javaPath.toAbsolutePath().normalize().toString(),
                "-jar",
                jarPath.toAbsolutePath().normalize().toString(),
                "client",
                "sync"
        ));
    }

    private static String executable(String name) {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? name + ".exe" : name;
    }
}
