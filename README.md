# ClipboardSync

ClipboardSync is a Spring Boot based relay server for synchronizing copied text between clients.

The goal is to keep a small relay service running on k3s, deployed as a Docker container, so that text copied on one machine can be pasted on another.

## Planned Architecture

- Windows client watches clipboard changes and sends copied text to the relay server.
- macOS client watches clipboard changes and sends copied text to the relay server.
- Relay server keeps the latest clipboard payload and distributes updates to connected clients.
- Clients apply remote clipboard updates locally so copy and paste works across machines.

## Network Assumption

- Clients connect through an HTTPS endpoint exposed by a tunnel or reverse proxy.
- The tunnel forwards traffic to the relay service inside the runtime environment.
- TLS is terminated before the Spring Boot application receives traffic.
- Clipboard contents must still be protected with end-to-end encryption because the relay server should not be able to read copied text.

## Target Stack

- Java
- Spring Boot
- Docker
- Gradle
- k3s

## Initial Scope

1. Create the Spring Boot relay server.
2. Define an API or WebSocket channel for clipboard update exchange.
3. Add Docker build support.
4. Add Kubernetes manifests for deployment to k3s.
5. Implement clients.

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

## Repository Status

This repository has been initialized with project documentation and ignore rules.
