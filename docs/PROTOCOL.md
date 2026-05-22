# ClipboardSync Protocol

ClipboardSync relays encrypted clipboard updates over WebSocket. The server validates routing metadata and message shape, but it never decrypts clipboard contents.

## Endpoint

Clients connect to the configured WebSocket path, defaulting to `/ws/clipboard`.

Deployment-specific public URLs, tunnel identifiers, internal service URLs, tokens, and keys must be supplied through local configuration and must not be committed to the repository.

## Handshake

Clients identify the device during the WebSocket handshake with one of the following:

- `X-Clipboard-Device-Id` header
- `deviceId` query parameter

Clients authenticate as a known device with Ed25519 signatures. The server stores only the public key for each allowed device ID.

The relay requires at least one configured device public key. Unauthenticated connections are not supported.

The client sends:

- `X-Clipboard-Timestamp`: current UTC timestamp in ISO-8601 format
- `X-Clipboard-Nonce`: random value generated for this handshake
- `X-Clipboard-Signature`: Base64 encoded Ed25519 signature

The signature input is:

```text
v1
deviceId=<device-id>
timestamp=<timestamp>
nonce=<nonce>
path=<websocket-path>
```

Requests outside the accepted clock skew are rejected. Reused nonces are also rejected while they remain in the server replay cache. Device private keys, real device IDs, and generated signatures must not be committed to the repository.

## Clipboard Update

```json
{
  "type": "clipboard_update",
  "updateId": "generated-update-id",
  "sourceDeviceId": "stable-device-id",
  "createdAt": "2026-05-21T00:00:00Z",
  "payloadType": "text",
  "payload": {
    "algorithm": "XChaCha20-Poly1305",
    "nonce": "base64-encoded-24-byte-nonce",
    "ciphertext": "base64-encoded-ciphertext-and-tag"
  }
}
```

The server rejects messages whose `sourceDeviceId` does not match the authenticated device.

## Latest State Replay

When a client connects and the server has a latest update from another device, the server replays the latest `clipboard_update` unchanged.

The relay must not rewrite authenticated metadata such as `type`, `updateId`, `sourceDeviceId`, `createdAt`, or `payloadType`, because clients use those fields as additional authenticated data.

## Encryption

Clients encrypt clipboard text with XChaCha20-Poly1305 before sending it to the server. The following metadata is used as additional authenticated data:

- `type`
- `updateId`
- `sourceDeviceId`
- `createdAt`
- `payloadType`

Clients must never reuse the same key and nonce pair.
