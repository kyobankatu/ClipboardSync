package com.clipboardsync.client.autostart;

import com.clipboardsync.ClipboardSyncApplication;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
        return jarPathFromProcessArguments()
                .orElseThrow(() -> new IllegalStateException(
                        "install-autostart must be run with java -jar <path-to-jar>"
                ));
    }

    private Optional<Path> jarPathFromProcessArguments() {
        return ProcessHandle.current()
                .info()
                .arguments()
                .flatMap(RuntimeCommandResolver::jarPathFromArguments);
    }

    private static Optional<Path> jarPathFromArguments(String[] arguments) {
        for (int index = 0; index < arguments.length - 1; index++) {
            if ("-jar".equals(arguments[index]) && arguments[index + 1].endsWith(".jar")) {
                return Optional.of(Path.of(arguments[index + 1]));
            }
        }
        return Optional.empty();
    }

    private static String executable(String name) {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? name + ".exe" : name;
    }
}
