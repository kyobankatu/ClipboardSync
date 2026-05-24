package com.clipboardsync.client;

import com.clipboardsync.client.autostart.AutostartService;
import com.clipboardsync.client.autostart.AutostartServiceFactory;

import java.net.http.HttpResponse;
import java.net.http.WebSocketHandshakeException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletionException;

/**
 * Command-line entry point for development client operations.
 */
public final class ClientCliApplication {

    private ClientCliApplication() {
    }

    /**
     * Runs the client command without starting the Spring Boot relay server.
     *
     * @param args client command arguments after the leading {@code client} token
     */
    public static void run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        try {
            switch (args[0]) {
                case "generate-keys" -> printGeneratedKeys();
                case "send" -> send(Arrays.copyOfRange(args, 1, args.length));
                case "listen" -> listen();
                case "sync" -> sync();
                case "install-autostart" -> installAutostart(Arrays.copyOfRange(args, 1, args.length));
                case "uninstall-autostart" -> uninstallAutostart();
                case "autostart-status" -> autostartStatus();
                default -> {
                    System.err.println("Unknown client command: " + args[0]);
                    printUsage();
                }
            }
        } catch (Exception exception) {
            System.err.println("Client command failed: " + describeFailure(exception));
            System.exit(1);
        }
    }

    private static String describeFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof WebSocketHandshakeException handshakeException) {
            HttpResponse<?> response = handshakeException.getResponse();
            return "WebSocket handshake failed with HTTP " + response.statusCode()
                    + ". Check serverUrl, deviceId, registered public key, and clock synchronization.";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getName();
        }
        return message;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void printGeneratedKeys() throws Exception {
        GeneratedClientKeys keys = ClientKeyGenerator.generate();
        System.out.println("Server registration value:");
        System.out.println("CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_<DEVICE_ID>=" + keys.ed25519PublicKey());
        System.out.println();
        System.out.println("Client-only values:");
        System.out.println("CLIPBOARD_SYNC_ED25519_PRIVATE_KEY=" + keys.ed25519PrivateKey());
        System.out.println("CLIPBOARD_SYNC_E2E_KEY=" + keys.e2eKey());
        System.out.println();
        System.out.println("Client properties template:");
        System.out.println("serverUrl=wss://relay.example.com/ws/clipboard");
        System.out.println("groupId=<GROUP_ID>");
        System.out.println("deviceId=<DEVICE_ID>");
        System.out.println("ed25519PrivateKey=" + keys.ed25519PrivateKey());
        System.out.println("e2eKey=" + keys.e2eKey());
        System.out.println("websocketPath=/ws/clipboard");
        System.out.println("clipboardPollIntervalMillis=500");
    }

    private static void send(String[] textParts) throws Exception {
        if (textParts.length == 0) {
            throw new IllegalArgumentException("send requires text");
        }
        ClientConfig config = ClientConfig.fromEnvironment();
        ClipboardRelayClient client = new ClipboardRelayClient(config);
        client.send(String.join(" ", textParts));
    }

    private static void listen() throws Exception {
        ClientConfig config = ClientConfig.fromEnvironment();
        ClipboardRelayClient client = new ClipboardRelayClient(config);
        client.listen();
    }

    private static void sync() throws Exception {
        ClientConfig config = ClientConfig.fromEnvironment();
        ClipboardRelayClient client = new ClipboardRelayClient(config);
        ClipboardService clipboardService = ClipboardServiceFactory.create();
        ClipboardWatcher clipboardWatcher = new ClipboardWatcher(clipboardService, config.clipboardPollInterval());
        new ClipboardSyncRunner(client, clipboardService, clipboardWatcher).run();
    }

    private static void installAutostart(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("install-autostart requires a jar path");
        }
        AutostartService autostartService = AutostartServiceFactory.create(Path.of(args[0]));
        autostartService.install();
        System.out.println("Autostart installed");
    }

    private static void uninstallAutostart() throws Exception {
        AutostartService autostartService = AutostartServiceFactory.create(Path.of("placeholder.jar"));
        autostartService.uninstall();
        System.out.println("Autostart uninstalled");
    }

    private static void autostartStatus() throws Exception {
        AutostartService autostartService = AutostartServiceFactory.create(Path.of("placeholder.jar"));
        System.out.println(autostartService.status());
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  client generate-keys");
        System.out.println("  client send <text>");
        System.out.println("  client listen");
        System.out.println("  client sync");
        System.out.println("  client install-autostart <jar-path>");
        System.out.println("  client uninstall-autostart");
        System.out.println("  client autostart-status");
    }
}
