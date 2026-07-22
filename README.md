<h1 align="center">Lyra Code</h1>

<p align="center">
  <img src="logo.png" alt="Lyra Code Logo" width="140" />
</p>

<p align="center">
  <strong>An Android-first local AI Agent app</strong>
</p>

<p align="center">
  <a href="README_zh-CN.md">简体中文</a> ·
  <a href="https://github.com/Soffd/Lyra-Code">GitHub</a> ·
  <a href="https://gitee.com/yukisoffd/lyra-code">Gitee</a>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-3.1.2-blue" />
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/license-Dual%20License%20%2F%20AGPLv3-orange" />
  <img alt="MCP" src="https://img.shields.io/badge/MCP-Client%20%2F%20Server-purple" />
  <img alt="File Transfer" src="https://img.shields.io/badge/File%20Transfer-WebDAV%20%2F%20FTP%20%2F%20SFTP-0A84FF" />
</p>

Lyra Code is an Android-first local AI Agent app. It brings model chat, an independent dual-pane file manager and code editor, native downloads, command execution, web search, MCP client/server support, SSH, WebDAV, FTP/FTPS/SFTP, a built-in mini server, backups, Skills, device diagnostics, and usage statistics into a mobile workflow.

## Screenshots

| Chat | Settings | Agent Tools |
| --- | --- | --- |
| <img src="example-img/chat.png" alt="Chat screen" width="260" /> | <img src="example-img/set.png" alt="Settings screen" width="260" /> | <img src="example-img/agent.png" alt="Agent tools screen" width="260" /> |

## Highlights

### Model Providers

- OpenAI Chat Completions-compatible APIs, Anthropic Messages API, and Gemini GenerateContent API.
- Multiple providers, API keys, base URLs, saved models, custom system prompts, and reasoning-depth presets.
- Fast provider/model/prompt switching from the chat action panel.
- HTTP API URLs are supported with an explicit security warning.

### Agent Tools

- Workspace/global file operations, file search, code editing, and change review.
- Native HTTP/HTTPS downloads with redirects, headers, progress, and optional SHA-256 verification.
- Command execution through Termux RunCommandService with stdout/stderr feedback; only obvious high-risk blacklist patterns are blocked.
- TODO planning, process records, file-change review, and diff visualization.
- Web search, web page reading, source annotation, and a configurable domain blacklist to avoid unwanted, spammy, or low-quality sources.
- Time/location awareness, configuration management, background tasks, and scheduled tasks.

### File Manager and Code Editor

- Independent dual-pane file manager rooted at `/storage/emulated/0`, with separately navigable panes designed for copying and moving files between directories.
- Active-pane highlighting, per-directory scroll restoration, animated navigation, swipe multi-selection, and confirmed batch copy, move, and delete operations.
- Create, rename, inspect, copy, move, delete, and extract files or folders; long-press `.html` and `.htm` files to preview them directly in an installed browser.
- Sora Editor with line numbers, TextMate syntax highlighting, in-file search, line navigation, word wrapping, and horizontal scrolling in no-wrap mode.
- UTF-8 decoding warnings, unsaved-change prompts, and automatic same-directory `.bak` backups before modified files are overwritten.
- Swipe open an editor-specific AI panel that receives the current file path silently, supports project workspaces, keeps approvals/tool output visible, and visually replays AI edits in the open document.

### Device and System

- Device information page covering manufacturer, model, Android version, CPU/hardware, ABI, memory, storage, display, network, Bluetooth, and battery state.
- Hardware inspection Agent for device diagnostics, troubleshooting, and hardware comparison.
- Installed-app recognition, optional Shizuku Shell, and optional Root command tools.
- System-level tools can be disabled independently.

### Remote Integrations

- MCP Client: connect to remote MCP servers over Streamable HTTP or SSE.
- MCP Server: expose Lyra Code's enabled local tools and connected MCP tools to other MCP clients over local/LAN HTTP, with configurable port and optional auth key.
- SSH: password/key login for Linux, Windows, and Git servers, with user-approved command execution.
- WebDAV: file listing, PROPFIND, search, upload, download, and cloud backup.
- FTP / FTPS / SFTP: add storage servers, list directories, search files, upload, and download through AI tools or manual configuration.
- Natural-language configuration management for MCP, SSH, WebDAV, FTP/FTPS/SFTP, Skills, and Agent tools.

### Mini Server

- Built-in local HTTP/HTTPS static file server for previewing and debugging local websites, documentation sites, and generated front-end projects.
- Configurable host, port, password authentication, custom domains, HTTPS certificate chain/private key, and force-HTTPS mode.
- Terminal-style live logs for connections, resource loading, 404/auth failures, and page JavaScript errors.
- The Agent can start/stop the mini server and inspect logs after user approval.

### Skills

- Import zip packages or a single `SKILL.md` file.
- Import a full GitHub / Gitee / GitLab repository.
- Manually create a Skill by editing `SKILL.md`.
- The Agent first reads `name` / `description` to judge relevance, then reads internal files on demand to avoid context bloat.

### Multimodal and Rendering

- Image upload, photo capture, cropping, rotation, brush annotation, and mosaic annotation.
- Thumbnail preview, full-screen preview, and save support for user/AI images and videos.
- Markdown, tables, syntax-highlighted code blocks, LaTeX math, and media Data URL rendering.
- Preview and save AI-returned base64 media, remote URLs, and local media files.

### Theme and Display

- Light, dark, and system-following theme modes.
- Optional Material You dynamic color, applying wallpaper-derived colors without changing the app layout.
- Adjustable font size with live preview, system-following mode, and a wider custom range.
- Refresh-rate preference: system smart mode, 30 Hz, 60 Hz, 90 Hz, or 120 Hz. The final effective rate still depends on device and Android display policy.

### Data and Backup

- Export/import profile data, conversations, model providers, MCP, SSH, WebDAV, FTP/FTPS/SFTP, system prompts, and Skills.
- Local zip backup and WebDAV cloud backup, with supplement import mode and deduplication.
- Safe export without API keys, or full migration export with secrets included.

### Usage Statistics

- Counts conversations, messages, user-input tokens, and AI-output tokens, including reasoning, tool context, file reads, command output, and repeated context.
- Offline token estimation without a network dependency.
- Daily, weekly, monthly, yearly, total, and historical date-range views.


## Project Structure

```text
app/                         Android application module
app/src/main/java/...        Kotlin / Jetpack Compose source code
app/src/main/assets/textmate/ TextMate grammars, language configurations, and themes
third_party/jlatexmath/      JLaTeXMath Android fork for LaTeX rendering
example-img/                 README screenshots
gradle/                      Gradle Wrapper configuration
```

## Build Requirements

- Android Studio or command-line Android SDK
- JDK 17
- Android SDK 36
- Gradle Wrapper

Build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is usually under:

```text
app/build/outputs/apk/debug/
```

For release builds, configure signing in Android Studio manually. Do not commit signing keys, keystores, API keys, `.env`, `local.properties`, or local private files.

## Termux Setup

Before using `run_command`, enable external app calls in Termux:

```bash
mkdir -p ~/.termux && (grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings
```

Then grant the Termux communication permission in Lyra Code settings. If permission is not granted, `run_command` is disabled automatically.

## Security Notes

Lyra Code may handle API keys, SSH passwords/private keys, MCP tokens, WebDAV/FTP credentials, conversations, local files, and remote server output. Please note:

- HTTP API, MCP, WebDAV, FTP, and mini server endpoints are insecure unless protected by HTTPS/TLS or a trusted network.
- Exposing the mini server to LAN, tunneling, or public networks may leak local files if the served directory, authentication, or HTTPS configuration is wrong. Review the workspace and password before enabling external access.
- Review tool calls before allowing AI to execute commands, edit files, upload/download files, or operate remote servers.
- The file manager requires Android's all-files access. Review selected paths carefully before confirming batch operations, and only preview trusted local HTML because browser scripts can execute with the browser's permissions.
- `run_command` no longer uses a fixed command allowlist, but it still blocks obvious high-risk operations such as `rm -rf /`, writes to `/dev/block`, and filesystem formatting commands. Review commands before approving them.
- The web search blacklist is stored locally. Plain domains are exact matches: `x.com` blocks only `x.com`, while `www.x.com` must be added separately. Use wildcard rules such as `*.x.com` to block subdomains; add both `x.com` and `*.x.com` when you want to block the root domain and all subdomains.
- Backup files containing secrets must be stored carefully and must not be shared publicly.
- This project does not verify that remote scripts, MCP servers, Skill repositories, or SSH commands are trustworthy.

## PR Notice

This project does not accept external pull requests. If you have feedback or suggestions, please submit an issue. If you need long-term modifications, fork this repository for independent maintenance or contact the repository owner.

## License

This project uses a dual-license model. The original Lyra Code source code is available under AGPLv3-or-later. A commercial license is required for closed-source distribution, private modifications, commercial exceptions, or use cases where you do not want to comply with AGPL copyleft obligations. Third-party components remain governed by their own licenses.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
