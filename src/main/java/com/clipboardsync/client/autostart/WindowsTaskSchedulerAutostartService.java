package com.clipboardsync.client.autostart;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
        Path appDirectory = createAppDirectory();
        Path startScript = appDirectory.resolve("clipboardsync-start.cmd");
        Files.writeString(startScript, startScript(commandResolver.resolve(), appDirectory), StandardCharsets.UTF_8);
        processRunner.run(List.of(
                "schtasks",
                "/Create",
                "/TN",
                TASK_NAME,
                "/SC",
                "ONLOGON",
                "/TR",
                quote(startScript.toString()),
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

    private String startScript(AutostartCommand command, Path appDirectory) {
        Path logDirectory = appDirectory.resolve("logs");
        StringBuilder builder = new StringBuilder("@echo off\r\n");
        for (int index = 0; index < command.arguments().size(); index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(quote(command.arguments().get(index)));
        }
        builder.append(" >> ").append(quote(logDirectory.resolve("clipboardsync.out.log").toString()));
        builder.append(" 2>> ").append(quote(logDirectory.resolve("clipboardsync.err.log").toString()));
        builder.append("\r\n");
        return builder.toString();
    }

    private Path createAppDirectory() throws Exception {
        String localAppData = environment.getOrDefault("LOCALAPPDATA", "");
        if (localAppData.isBlank()) {
            throw new IllegalStateException("LOCALAPPDATA is required to install Windows autostart");
        }
        Path appDirectory = Path.of(localAppData, "ClipboardSync");
        Files.createDirectories(appDirectory.resolve("logs"));
        return appDirectory;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
