# TASKS

この計画は、ClipboardSync の実装作業を対象にします。Docker イメージ作成、k3s への配置、Kubernetes マニフェスト作成などのデプロイ作業は含めません。

## 1. 要件整理

- 同期対象をプレーンテキストに限定する。
- 1ユーザー、複数端末の利用を初期スコープにする。
- クリップボード更新の方向は Windows から macOS、macOS から Windows の双方向にする。
- 同じ内容を受信して再送信し続けるループを防ぐ。
- サーバー再起動後は過去のクリップボード内容を保持しない方針にする。
- TLS と XChaCha20-Poly1305 によるエンドツーエンド暗号化を初期スコープに含める。
- サーバーはクリップボード本文を復号できず、暗号化済みペイロードのみを中継する。
- 初期実装では画像、ファイル、リッチテキスト、履歴管理は扱わない。

## 2. 中継サーバー

- Spring Boot プロジェクトを作成する。
- クリップボード更新を表すドメインモデルを定義する。
  - 端末ID
  - ペイロード種別
  - 暗号化方式
  - nonce
  - 暗号化済み本文
  - 追加認証データの対象フィールド
  - 更新時刻
  - 更新ID
- クライアント接続方式を決める。
  - 初期候補は WebSocket。
  - クライアントからの接続先は Cloudflare Tunnel などのトンネルまたはリバースプロキシ経由のHTTPS URLを想定する。
  - 実ドメイン、トンネルID、内部サービスURLは公開リポジトリに書かず、環境変数または非公開の運用メモで管理する。
  - アプリケーションはトンネル先またはプロキシ先のHTTPサービスとして待ち受けられるようにする。
  - REST API はヘルスチェックと簡易確認用途に限定する。
- WebSocket エンドポイントを実装する。
  - クライアント登録
  - クリップボード更新受信
  - 接続中クライアントへの更新配信
  - 送信元クライアントへのエコーバック抑制
- 最新のクリップボード状態をメモリ上で保持する。
- 保持する最新状態は暗号化済みペイロードに限定する。
- 接続中クライアント一覧を管理する。
- 不正なメッセージを拒否するバリデーションを実装する。
- サーバー側では復号処理を実装しない。
- サーバー状態確認用のヘルスチェックエンドポイントを追加する。
- ログ出力を整理する。
  - 接続
  - 切断
  - 更新受信
  - 配信失敗

## 3. 同期プロトコル

- クライアントとサーバー間のメッセージ形式を定義する。
- メッセージ種別を定義する。
  - hello
  - clipboard_update
  - error
- clipboard_update は平文ではなく、暗号化済みペイロードを送る形式にする。
- 最新状態の再送信時も、追加認証データと矛盾しないように暗号化時のメタデータを書き換えない。
- 更新IDの生成ルールを決める。
- 端末IDの生成と保存方法を決める。
- ループ防止のため、受信したリモート更新には送信元更新IDを保持する。
- 最大ペイロードサイズを決める。
- 改行、空文字、Unicode文字列の扱いを確認する。
- nonce の生成方式を決める。
- 同じ鍵と nonce の組み合わせを再利用しないことをテストで保証する。
- 追加認証データに含めるフィールドを決める。
  - message type
  - update ID
  - source device ID
  - created at
  - payload type
- プロトコル仕様を README または別ドキュメントに反映する。

## 4. Windows クライアント

- 実装言語と実行形態を決める。
  - 初期候補は Java/Kotlin、C#、または軽量な常駐プロセス。
- クリップボード監視を実装する。
- テキストコピー時に本文を XChaCha20-Poly1305 で暗号化し、サーバーへ clipboard_update を送信する。
- サーバーから受信した clipboard_update を復号し、ローカルクリップボードに反映する。
- 自分が反映したリモート更新を再送信しない仕組みを実装する。
- サーバー切断時の再接続処理を実装する。
- 設定ファイルでサーバーURLと端末名を指定できるようにする。
- 端末ローカルの共有鍵または鍵素材の保存方法を決める。
- 常駐中のログ出力を実装する。

## 5. macOS クライアント

- 実装言語と実行形態を決める。
  - 初期候補は Java/Kotlin、Swift、または軽量な常駐プロセス。
- クリップボード監視を実装する。
- テキストコピー時に本文を XChaCha20-Poly1305 で暗号化し、サーバーへ clipboard_update を送信する。
- サーバーから受信した clipboard_update を復号し、ローカルクリップボードに反映する。
- 自分が反映したリモート更新を再送信しない仕組みを実装する。
- サーバー切断時の再接続処理を実装する。
- 設定ファイルでサーバーURLと端末名を指定できるようにする。
- 端末ローカルの共有鍵または鍵素材の保存方法を決める。
- 常駐中のログ出力を実装する。

## 6. セキュリティ

- WebSocket 接続時に端末IDごとのEd25519署名を検証する。
- サーバー側に登録されたMacとWindowsの端末IDだけを許可する。
- サーバーには端末ごとの公開鍵のみを登録し、端末の秘密鍵はMac/Windows側だけに置く。
- 共有Bearerトークン方式やサーバー保管secret方式にはしない。
- 署名には端末ID、timestamp、nonce、WebSocket pathを含める。
- timestampが許容範囲外の接続を拒否する。
- 端末公開鍵が未設定の場合はアプリケーションを起動失敗にする。
- 開発用の無認証モードは実装しない。
- ログにクリップボード本文を出さない方針にする。
- TLS は通信経路の保護として必須にする。
- TLS は Cloudflare Tunnel などのトンネルまたはリバースプロキシ側で終端され、Spring Boot アプリケーションは内部HTTPサービスとして受ける前提にする。
- アプリケーション内では `X-Forwarded-*` 系ヘッダーを扱えるようにし、外部URLの生成やログで誤ったスキームを使わないようにする。
- E2E 暗号化はクライアント側で行い、サーバーは暗号文のみを中継する。
- XChaCha20-Poly1305 を使える暗号ライブラリを選定する。
  - 標準JCAだけに依存できない可能性があるため、実装前に対応ライブラリを確認する。
  - Windows/macOS クライアントとサーバーで同じ暗号仕様を共有できることを条件にする。
- 暗号化対象をクリップボード本文に限定し、ルーティングに必要なメタデータは最小限だけ平文で送る。
- 追加認証データを使い、平文メタデータの改ざんを検出する。
- 鍵管理方式を決める。
  - 初期候補は各端末に同じ共有鍵を手動設定する方式。
  - 共有鍵は環境変数、OSキーチェーン、または設定ファイルから読み込めるようにする。
  - 設定ファイルに保存する場合はファイル権限の確認を実装する。
- サーバー側ログには暗号文、nonce、署名、公開鍵、共有鍵、クリップボード本文を出さない。
- 復号失敗時はクリップボードへ反映せず、最小限のエラーログだけ出す。
- 公開ドキュメントには実ドメイン、内部URL、トンネル識別子、秘密鍵、共有鍵を書かない。
- 設定例では `CLIPBOARD_SYNC_SERVER_URL` のような環境変数名とプレースホルダー値だけを使う。

## 7. Javadoc とドキュメント

- 実装と同時に public class、public method、主要な package へ Javadoc を書く。
- 暗号化、同期プロトコル、ループ防止、再接続に関わるクラスは設計意図を Javadoc に含める。
- Javadoc にはクリップボード本文、共有鍵、トークンなどの秘密情報を例示しない。
- 複雑なフィールドには、単位、形式、null可否、セキュリティ上の注意点を書く。
- README には利用者向けの起動方法と制限事項を書く。
- プロトコル仕様は README から参照できる形で整理する。

## 8. テスト

- サーバーの単体テストを追加する。
  - メッセージバリデーション
  - 更新保持
  - 送信元へのエコーバック抑制
- WebSocket の結合テストを追加する。
  - 複数クライアント接続
  - Windows 相当から macOS 相当への配信
  - macOS 相当から Windows 相当への配信
  - 未登録端末の拒否
  - 不正署名の拒否
  - nonce 再利用の拒否
- クライアントのクリップボード監視処理を分離し、テスト可能な形にする。
- ループ防止のテストを追加する。
- 空文字、大きい文字列、Unicode文字列のテストを追加する。
- 暗号化と復号のテストを追加する。
  - 正常に復号できること
  - nonce が異なること
  - 追加認証データが改ざんされた場合に復号失敗すること
  - 誤った共有鍵では復号失敗すること
  - 復号失敗時にクリップボードへ反映されないこと
- Javadoc 生成をビルドまたはCIの確認対象に含める。

## 9. 実装順序

1. Spring Boot + Gradle プロジェクトを作成する。
2. WebSocket の最小プロトコルを定義する。
3. XChaCha20-Poly1305 を使う暗号化モジュールを作成する。
4. サーバーで暗号化済み clipboard_update の受信と配信を実装する。
5. 実装対象の public API に Javadoc を追加する。
6. サーバーの単体テストと結合テストを追加する。
7. 簡易CLIクライアントを作り、双方向同期とE2E暗号化の動作確認を行う。
8. Windows クライアントのクリップボード監視を実装する。
9. macOS クライアントのクリップボード監視を実装する。
10. ループ防止、再接続、設定ファイル対応を固める。
11. Ed25519 公開鍵認証を固める。
12. Javadoc 生成確認を追加する。
13. README に起動方法、設定項目、制限事項を追記する。

## 10. CLI Client Implementation Plan

The next implementation phase is a Java CLI client. The purpose is to validate the full protocol before implementing OS-specific clipboard watchers.

### Goals

- Connect to the relay server over WebSocket.
- Authenticate as a registered device with an Ed25519 private key.
- Encrypt outgoing clipboard text with XChaCha20-Poly1305.
- Send encrypted `clipboard_update` messages to the relay.
- Receive encrypted updates from other devices.
- Decrypt received updates locally and print the plaintext to stdout.
- Confirm that the relay never sees clipboard plaintext.
- Confirm that the sender does not receive its own update back.

### Package Structure

- Create `src/main/java/com/clipboardsync/client`.
- Keep client-only code separate from server relay code.
- Reuse shared protocol and crypto classes where appropriate.

### Client Configuration

Create a client configuration model that can be loaded from command-line arguments or environment variables.

Required values:

- `serverUrl`: public relay WebSocket URL, for example a placeholder `wss://relay.example.com/ws/clipboard`.
- `deviceId`: stable local device identifier.
- `ed25519PrivateKey`: Base64 encoded Ed25519 private key for device authentication.
- `e2eKey`: Base64 encoded 32-byte XChaCha20-Poly1305 key for clipboard encryption.
- `websocketPath`: path used in the handshake signature.

Do not commit real private keys, E2E keys, device IDs, domains, or tunnel details.

### Key Generation Command

Add a CLI command that generates local development keys.

Example command:

```bash
./gradlew bootRun --args="client generate-keys"
```

The command should generate:

- Ed25519 private key for the client device.
- Ed25519 public key to register on the server.
- XChaCha20-Poly1305 32-byte E2E key shared by trusted client devices.

Output must clearly separate:

- Values safe to register on the server.
- Values that must stay only on client devices.

### Handshake Signing

Create a `DeviceHandshakeSigner` component.

It should sign the exact same input that the server verifies:

```text
v1
deviceId=<device-id>
timestamp=<timestamp>
nonce=<nonce>
path=<websocket-path>
```

The signer should produce:

- `X-Clipboard-Device-Id`
- `X-Clipboard-Timestamp`
- `X-Clipboard-Nonce`
- `X-Clipboard-Signature`

### WebSocket Client

Create a `ClipboardRelayClient` component.

Responsibilities:

- Open a WebSocket connection with the signed authentication headers.
- Serialize and send `ClipboardMessage` instances.
- Parse received messages.
- Ignore updates from the same `deviceId`.
- Surface connection and protocol errors without logging secrets.
- Keep reconnection minimal in the first version.

### Manual Send Command

Add a command for manually sending text.

Example:

```bash
./gradlew bootRun --args="client send 'hello from mac'"
```

The command should:

- Build a `clipboard_update` message.
- Generate a new update ID.
- Encrypt the text with XChaCha20-Poly1305.
- Include deterministic associated data.
- Send the message to the relay.

### Listen Command

Add a command that waits for incoming clipboard updates.

Example:

```bash
./gradlew bootRun --args="client listen"
```

The command should:

- Connect to the relay.
- Wait for incoming encrypted updates.
- Decrypt valid updates with the local E2E key.
- Print plaintext to stdout.
- Reject messages that fail decryption or metadata authentication.

### Local Verification Flow

Run two terminal sessions.

Terminal A:

```bash
./gradlew bootRun --args="client listen"
```

Terminal B:

```bash
./gradlew bootRun --args="client send 'test clipboard text'"
```

Expected result:

- Terminal A prints `test clipboard text`.
- Terminal B does not receive its own update.
- The server logs no plaintext.

### Tests

Add focused tests for:

- Ed25519 key generation and public key export.
- Handshake signing compatibility with server verification.
- XChaCha20-Poly1305 encryption and decryption through the client path.
- Associated data mismatch rejection.
- Invalid private key handling.
- Invalid E2E key handling.
- Message serialization compatibility with the server protocol.

### Out of Scope for This Phase

- Native clipboard watching.
- `pbcopy` / `pbpaste` integration.
- PowerShell `Get-Clipboard` / `Set-Clipboard` integration.
- Background daemon mode.
- GUI.
- Auto-start on login.
- Production deployment changes.

## 11. File-Based Key Configuration Plan

The next usability improvement is to remove repeated copy/paste of keys from command lines.

### Server Public Key Configuration

- Allow the relay server to load trusted client public keys from a directory.
- Use one file per device ID.
- Default or configured directory example: `/config/device-public-keys`.
- Each file name is the device ID.
- Each file content is the Base64 encoded Ed25519 public key.
- Keep environment variable support as a fallback for simple local testing.
- Treat public keys as non-secret, but still avoid committing real personal device identifiers when publishing examples.
- Fail startup if no public key is found from either configured properties, environment variables, or the public key directory.

Example mounted files:

```text
/config/device-public-keys/MACBOOK
/config/device-public-keys/WINDOWS
```

### Client Local Configuration File

- Allow CLI clients to load configuration from a local properties file.
- Default path on macOS/Linux: `~/.config/clipboardsync/client.properties`.
- Default path on Windows: `%APPDATA%\ClipboardSync\client.properties`.
- Allow overriding the path with `CLIPBOARD_SYNC_CLIENT_CONFIG`.
- Keep environment variable support as an override for testing.
- Store the following values in the file:
  - `serverUrl`
  - `deviceId`
  - `ed25519PrivateKey`
  - `e2eKey`
  - `websocketPath`
- Never commit real client configuration files.

Example client file:

```properties
serverUrl=wss://relay.example.com/ws/clipboard
deviceId=MACBOOK
ed25519PrivateKey=<client-only-private-key>
e2eKey=<shared-e2e-key>
websocketPath=/ws/clipboard
```

### Client File Permission Checks

- On POSIX systems, reject or warn when the client config file is readable by group or others.
- Start with a warning to avoid blocking Windows and unusual filesystems.
- Recommend `chmod 600 ~/.config/clipboardsync/client.properties`.
- Do not log secret values from the config file.

### Key Generation Usability

- Update `client generate-keys` output to include a client properties template.
- Clearly label server-safe public key output.
- Clearly label client-only private key and E2E key output.
- Do not automatically write files unless a later explicit command is added.

### Tests

- Test server public key loading from a temporary directory.
- Test client config loading from a temporary properties file.
- Test environment variables overriding file values.
- Test missing config values produce clear errors.
- Test POSIX-readable-by-group/others detection when supported by the filesystem.

## 12. 後回しにする項目

- クリップボード履歴
- 画像やファイルの同期
- 複数ユーザー対応
- 端末ごとの同期許可設定
- Web UI
- 管理API
- 永続化

## 13. Native Text Clipboard Sync Plan

This phase turns the development CLI client into a usable text clipboard synchronizer for macOS and Windows.

### Goals

- Add a `client sync` command.
- Read local text clipboard changes from the operating system.
- Encrypt local text changes and send them through the existing relay protocol.
- Decrypt remote text updates and write them into the local operating system clipboard.
- Prevent remote clipboard writes from being sent back to the relay as new local updates.
- Keep the implementation testable by separating clipboard access from synchronization logic.

### Clipboard Abstraction

- Add a `ClipboardService` interface.
- Expose `readText()` for reading the current text clipboard value.
- Expose `writeText(String text)` for writing text into the clipboard.
- Return an empty result when the clipboard does not currently contain text.
- Do not support images, files, rich text, or clipboard history in this phase.

### AWT Clipboard Implementation

- Add an `AwtClipboardService` implementation using `java.awt.Toolkit` and `java.awt.datatransfer`.
- Use `DataFlavor.stringFlavor` only.
- Detect headless environments and fail with a clear message.
- Keep clipboard plaintext out of logs.

### Clipboard Watcher

- Add a polling `ClipboardWatcher`.
- Make the poll interval configurable.
- Default to a conservative interval such as 500 milliseconds.
- Emit a change only when the local clipboard text differs from the last observed text.
- Ignore empty or non-text clipboard values for the first implementation.
- Keep the watcher independent from WebSocket code so it can be unit tested with fake clipboard services.

### Sync Runner

- Add a `ClipboardSyncRunner` that combines:
  - `ClipboardRelayClient`
  - `ClipboardWatcher`
  - `ClipboardService`
- Maintain a persistent WebSocket connection.
- Send local clipboard changes as encrypted `clipboard_update` messages.
- Apply decrypted remote updates to the local clipboard.
- Stop cleanly when the WebSocket closes or the process is interrupted.

### Loop Prevention

- Track text written from remote updates.
- When the watcher sees the same value immediately after a remote write, suppress sending it back.
- Continue sending future user-initiated changes even when they match older remote values after an intervening local value.
- Ignore messages whose `sourceDeviceId` matches the local device ID.

### Client Configuration

- Add optional client properties:
  - `clipboardPollIntervalMillis`
- Add environment override:
  - `CLIPBOARD_SYNC_CLIPBOARD_POLL_INTERVAL_MILLIS`
- Validate that the poll interval is positive.
- Keep existing file-based key and URL configuration behavior.

### Tests

- Add tests for `ClipboardWatcher` using a fake clipboard implementation.
- Add tests for loop suppression in the sync runner.
- Add tests that the poll interval is loaded from file and can be overridden by environment variables.
- Add tests that invalid poll intervals fail clearly.
- Continue running both unit tests and Javadoc generation after implementation.
