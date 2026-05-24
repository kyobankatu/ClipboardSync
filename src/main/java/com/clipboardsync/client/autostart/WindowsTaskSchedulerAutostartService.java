package com.clipboardsync.client.autostart;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Windows Task Scheduler implementation for login autostart.
 */
public class WindowsTaskSchedulerAutostartService implements AutostartService {

    private static final String TASK_NAME = "ClipboardSync";

    private final RuntimeCommandResolver commandResolver;
    private final ProcessRunner processRunner;
    private final Map<String, String> environment;

    /**
     * Creates the Windows autostart service.
     *
     * @param commandResolver command resolver
     * @param processRunner command runner
     * @param environment process environment
     */
    public WindowsTaskSchedulerAutostartService(
            RuntimeCommandResolver commandResolver,
            ProcessRunner processRunner,
            Map<String, String> environment
    ) {
        this.commandResolver = commandResolver;
        this.processRunner = processRunner;
        this.environment = environment;
    }

    @Override
    public void install() throws Exception {
        createLogDirectory();
        processRunner.run(List.of(
                "schtasks",
                "/Create",
                "/TN",
                TASK_NAME,
                "/SC",
                "ONLOGON",
                "/TR",
                commandLine(commandResolver.resolve()),
                "/F"
        ));
    }

    @Override
    public void uninstall() throws Exception {
        processRunner.run(List.of("schtasks", "/Delete", "/TN", TASK_NAME, "/F"));
    }

    @Override
    public String status() throws Exception {
        try {
            processRunner.run(List.of("schtasks", "/Query", "/TN", TASK_NAME));
            return "Autostart is installed: " + TASK_NAME;
        } catch (Exception exception) {
            return "Autostart is not installed";
        }
    }

    private String commandLine(AutostartCommand command) {
        String localAppData = environment.getOrDefault("LOCALAPPDATA", "");
        StringBuilder builder = new StringBuilder();
        for (String argument : command.arguments()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(quote(argument));
        }
        if (!localAppData.isBlank()) {
            builder.append(" >> ").append(quote(localAppData + "\\ClipboardSync\\logs\\clipboardsync.out.log"));
            builder.append(" 2>> ").append(quote(localAppData + "\\ClipboardSync\\logs\\clipboardsync.err.log"));
        }
        return builder.toString();
    }

    private void createLogDirectory() throws Exception {
        String localAppData = environment.getOrDefault("LOCALAPPDATA", "");
        if (!localAppData.isBlank()) {
            Files.createDirectories(Path.of(localAppData, "ClipboardSync", "logs"));
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
