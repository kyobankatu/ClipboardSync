# ClipboardSync

ClipboardSync is a Spring Boot based relay server and development CLI for synchronizing copied text between trusted clients.

The goal is to let text copied on one machine be pasted on another while keeping clipboard plaintext end-to-end encrypted.

## Planned Architecture

- Windows client watches clipboard changes and sends copied text to the relay server.
- macOS client watches clipboard changes and sends copied text to the relay server.
- Relay server keeps the latest clipboard payload and distributes updates to connected clients.
- Clients apply remote clipboard updates locally so copy and paste works across machines.

## Target Stack

- Java
- Spring Boot
- Gradle

## Initial Scope

1. Create the Spring Boot relay server.
2. Define an API or WebSocket channel for clipboard update exchange.
3. Authenticate trusted clients with Ed25519 signatures.
4. Relay only encrypted clipboard payloads.
5. Provide a development CLI client for manual send/listen testing.

## Protocol

The WebSocket protocol is documented in [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Configuration

Runtime settings are supplied through environment variables.

- `PORT`: HTTP port used by the Spring Boot application. Defaults to `8080`.
- `CLIPBOARD_SYNC_WEBSOCKET_PATH`: WebSocket path. Defaults to `/ws/clipboard`.
- `CLIPBOARD_SYNC_ALLOWED_ORIGINS`: comma-separated allowed WebSocket origins. Defaults to `*`.
- `CLIPBOARD_SYNC_MAX_CIPHERTEXT_BYTES`: maximum decoded ciphertext size. Defaults to `65536`.
- `CLIPBOARD_SYNC_DEVICE_PUBLIC_KEYS_<DEVICE_ID>`: Base64 encoded Ed25519 public key for an allowed client device.

Use placeholder values in committed documentation and keep real domains, tunnel details, tokens, and keys in private configuration.

## Development CLI Client

Generate development keys:

```bash
./gradlew bootRun --args="client generate-keys"
```

Register the generated Ed25519 public key on the relay server. Keep the generated Ed25519 private key and E2E key only on trusted client devices.

Client environment variables:

- `CLIPBOARD_SYNC_SERVER_URL`: relay WebSocket URL, for example `wss://relay.example.com/ws/clipboard`.
- `CLIPBOARD_SYNC_DEVICE_ID`: local device ID matching a registered public key.
- `CLIPBOARD_SYNC_ED25519_PRIVATE_KEY`: Base64 encoded Ed25519 private key for this device.
- `CLIPBOARD_SYNC_E2E_KEY`: Base64 encoded 32-byte XChaCha20-Poly1305 key shared by trusted clients.
- `CLIPBOARD_SYNC_WEBSOCKET_PATH`: optional path included in the handshake signature. Defaults to the path in `CLIPBOARD_SYNC_SERVER_URL`.

Listen for encrypted updates:

```bash
./gradlew bootRun --args="client listen"
```

Send encrypted text manually:

```bash
./gradlew bootRun --args="client send 'hello from mac'"
```

## Repository Status

This repository has been initialized with project documentation and ignore rules.
