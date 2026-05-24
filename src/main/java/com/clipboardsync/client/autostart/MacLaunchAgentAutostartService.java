package com.clipboardsync.client.autostart;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS LaunchAgent implementation for login autostart.
 */
public class MacLaunchAgentAutostartService implements AutostartService {

    private static final String LABEL = "com.clipboardsync.client";

    private final RuntimeCommandResolver commandResolver;
    private final ProcessRunner processRunner;
    private final Path plistPath;
    private final Path logDirectory;

    /**
     * Creates the default macOS LaunchAgent service.
     *
     * @param commandResolver command resolver
     * @param processRunner command runner
     */
    public MacLaunchAgentAutostartService(RuntimeCommandResolver commandResolver, ProcessRunner processRunner) {
        this(
                commandResolver,
                processRunner,
                Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", LABEL + ".plist"),
                Path.of(System.getProperty("user.home"), ".local", "state", "clipboardsync")
        );
    }

    MacLaunchAgentAutostartService(
            RuntimeCommandResolver commandResolver,
            ProcessRunner processRunner,
            Path plistPath,
            Path logDirectory
    ) {
        this.commandResolver = commandResolver;
        this.processRunner = processRunner;
        this.plistPath = plistPath;
        this.logDirectory = logDirectory;
    }

    @Override
    public void install() throws Exception {
        Files.createDirectories(plistPath.getParent());
        Files.createDirectories(logDirectory);
        Files.writeString(plistPath, plist(commandResolver.resolve()), StandardCharsets.UTF_8);
        try {
            processRunner.run(List.of("launchctl", "bootout", "gui/" + uid() + "/" + LABEL));
        } catch (Exception ignored) {
            // The service may not be loaded yet.
        }
        processRunner.run(List.of("launchctl", "bootstrap", "gui/" + uid(), plistPath.toString()));
        processRunner.run(List.of("launchctl", "enable", "gui/" + uid() + "/" + LABEL));
    }

    @Override
    public void uninstall() throws Exception {
        try {
            processRunner.run(List.of("launchctl", "bootout", "gui/" + uid() + "/" + LABEL));
        } catch (Exception ignored) {
            // The service may already be unloaded.
        }
        Files.deleteIfExists(plistPath);
    }

    @Override
    public String status() {
        return Files.exists(plistPath)
                ? "Autostart is installed: " + plistPath
                : "Autostart is not installed";
    }

    private String plist(AutostartCommand command) {
        StringBuilder arguments = new StringBuilder();
        for (String argument : command.arguments()) {
            arguments.append("      <string>").append(xml(argument)).append("</string>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" \
                "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                %s    </array>
                    <key>RunAtLoad</key>
                    <true/>
                    <key>KeepAlive</key>
                    <true/>
                    <key>StandardOutPath</key>
                    <string>%s</string>
                    <key>StandardErrorPath</key>
                    <string>%s</string>
                  </dict>
                </plist>
                """.formatted(
                LABEL,
                arguments,
                xml(logDirectory.resolve("clipboardsync.out.log").toString()),
                xml(logDirectory.resolve("clipboardsync.err.log").toString())
        );
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String uid() {
        return ProcessHandle.current().info().user()
                .map(ignored -> System.getProperty("user.name"))
                .flatMap(MacLaunchAgentAutostartService::idUser)
                .orElseThrow(() -> new IllegalStateException("Failed to resolve current uid"));
    }

    private static java.util.Optional<String> idUser(String user) {
        try {
            Process process = new ProcessBuilder(new ArrayList<>(List.of("id", "-u", user))).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            return exitCode == 0 && !output.isBlank() ? java.util.Optional.of(output) : java.util.Optional.empty();
        } catch (Exception exception) {
            return java.util.Optional.empty();
        }
    }
}
