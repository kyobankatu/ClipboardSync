# ClipboardSync

ClipboardSync is a Spring Boot based relay server for synchronizing copied text between clients.

The goal is to keep a small relay service running on k3s, deployed as a Docker container, so that text copied on one machine can be pasted on another.

## Planned Architecture

- Windows client watches clipboard changes and sends copied text to the relay server.
- macOS client watches clipboard changes and sends copied text to the relay server.
- Relay server keeps the latest clipboard payload and distributes updates to connected clients.
- Clients apply remote clipboard updates locally so copy and paste works across machines.

## Target Stack

- Java
- Spring Boot
- Docker
- k3s

## Initial Scope

1. Create the Spring Boot relay server.
2. Define an API or WebSocket channel for clipboard update exchange.
3. Add Docker build support.
4. Add Kubernetes manifests for deployment to k3s.
5. Implement clients.

## Repository Status

This repository has been initialized with project documentation and ignore rules.

