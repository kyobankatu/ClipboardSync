package com.clipboardsync.client;

import java.util.Arrays;

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
                default -> {
                    System.err.println("Unknown client command: " + args[0]);
                    printUsage();
                }
            }
        } catch (Exception exception) {
            System.err.println("Client command failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void printGeneratedKeys() throws Exception {
        GeneratedClientKeys keys = ClientKeyGenerator.generate();
        System.out.println("Server registration value:");
        System.out.println("CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_<DEVICE_ID>=" + keys.ed25519PublicKey());
        System.out.println();
        System.out.println("Client-only values:");
        System.out.println("CLIPBOARD_SYNC_ED25519_PRIVATE_KEY=" + keys.ed25519PrivateKey());
        System.out.println("CLIPBOARD_SYNC_E2E_KEY=" + keys.e2eKey());
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

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  client generate-keys");
        System.out.println("  client send <text>");
        System.out.println("  client listen");
    }
}
