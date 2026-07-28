<h1 align="center">Lyra Code</h1>

<p align="center">
  <img src="logo.png" alt="Lyra Code Logo" width="140" />
</p>

<p align="center">
  <strong>面向 Android 的本地 AI Agent 应用</strong>
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="https://github.com/Soffd/Lyra-Code">GitHub</a> ·
  <a href="https://gitee.com/yukisoffd/lyra-code">Gitee</a>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-3.3.0-blue" />
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/license-Dual%20License%20%2F%20AGPLv3-orange" />
  <img alt="MCP" src="https://img.shields.io/badge/MCP-Client%20%2F%20Server-purple" />
  <img alt="File Transfer" src="https://img.shields.io/badge/File%20Transfer-WebDAV%20%2F%20FTP%20%2F%20SFTP-0A84FF" />
</p>

Lyra Code 是一个面向 Android 的本地 AI Agent 应用。它把大模型对话、独立双栏文件管理器与代码编辑器、原生文件下载、命令执行、联网搜索、MCP 客户端/服务端、SSH、WebDAV、FTP/FTPS/SFTP、内置微型服务器、数据备份、Skills、设备诊断和用量统计整合到移动端。

## 界面预览

| AI 对话 | 设置 | Agent 工具 |
| --- | --- | --- |
| <img src="example-img/chat.png" alt="AI 对话页" width="260" /> | <img src="example-img/set.png" alt="设置页" width="260" /> | <img src="example-img/agent.png" alt="Agent 工具页" width="260" /> |

## 核心能力

### 多模型与多接口

- 支持 OpenAI Chat Completions 兼容接口、Anthropic Messages API 和 Gemini GenerateContent API。
- 可保存多个模型服务商、API Key、基础 URL、预设模型、自定义系统提示词和推理深度。
- 对话底部动作面板可快速切换服务商、模型和提示词。
- 支持 HTTP API URL，但会提示明文连接风险。

### Agent 工具

- 工作区/全局文件操作、文件搜索、代码编辑和变更审查。
- 应用原生 HTTP/HTTPS 下载，支持重定向、请求头、进度显示和可选 SHA-256 校验。
- 命令执行与 Termux RunCommandService 集成，支持 stdout/stderr 回传；仅拦截明显高危黑名单模式。
- TODO 规划、过程记录、文件变更审查和差异可视化。
- 联网搜索、网页读取、来源标注和网站黑名单，可避免 AI 打开指定域名的垃圾内容或不希望引用的网站。
- 时间/地理感知、配置管理、后台任务和定时任务。

### 文件管理器与代码编辑器

- 独立双栏文件管理器默认进入 `/storage/emulated/0`，两栏可分别浏览目录，适合在不同目录之间复制和移动文件。
- 支持活动栏高亮、目录滚动位置恢复、进出目录动画、滑动多选，以及带二次确认的批量复制、移动和删除。
- 支持新建、重命名、查看属性、复制、移动、删除和解压文件或文件夹；长按 `.html`、`.htm` 文件可直接调用已安装浏览器预览。
- 集成 Sora Editor，支持行号、TextMate 语法高亮、文件内搜索、跳转到行、自动换行，以及不换行模式下横向浏览。
- 默认按 UTF-8 打开文件，遇到解码错误会警告；退出时检查未保存修改，覆盖修改前自动在同目录生成 `.bak` 备份。
- 编辑器内可右滑打开专用 AI 对话侧栏，静默传入当前文件路径、支持添加项目工作目录，并在编辑页保留审批和工具输出、可视化重放 AI 修改。

### 设备与系统

- 手机信息页面可查看厂商、型号、Android 版本、CPU/硬件、ABI、内存、存储、屏幕、网络、蓝牙和电池状态。
- 硬件检查 Agent 可辅助机型诊断、问题排查和设备硬件对比。
- 应用列表识别、可选 Shizuku Shell 和可选 Root 命令工具。
- 系统级 Agent 工具均可单独禁用。

### 远程集成

- MCP 客户端：支持连接 Streamable HTTP / SSE 远程 MCP Server。
- MCP 服务端：可将 Lyra Code 当前启用的本地工具和已连接 MCP 工具，通过本机/局域网 HTTP 暴露给其他 MCP 客户端调用，支持自定义端口和可选认证 Key。
- SSH：支持密码或密钥登录 Linux、Windows、Git 服务器，并执行经用户确认的命令。
- WebDAV：支持列出文件、PROPFIND、搜索、上传、下载和云备份。
- FTP / FTPS / SFTP：可添加存储服务器，支持列目录、搜索文件、上传和下载，可手动配置或由 AI 管理。
- 支持通过自然语言管理 MCP、SSH、WebDAV、FTP/FTPS/SFTP、Skills 和 Agent 工具配置。

### 微型服务器

- 内置本地 HTTP/HTTPS 静态文件服务器，可用于预览和调试本地网站、文档站和生成的前端项目。
- 支持自定义监听主机、端口、密码认证、绑定域名、HTTPS 证书链/私钥和强制 HTTPS。
- 提供类终端实时日志，可查看连接、资源加载、404/认证失败和页面 JavaScript 报错。
- AI 可在用户确认后启动/关闭微型服务器，并读取日志辅助定位问题。

### Skills 能力包

- 支持从文件导入 zip 或单个 `SKILL.md`。
- 支持从 GitHub / Gitee / GitLab 仓库链接导入。
- 支持手动编辑 `SKILL.md` 创建 Skill。
- Agent 会先读取 `name` / `description` 判断是否相关，再按需读取 Skill 内部文件，避免上下文爆炸。

### 多模态与内容渲染

- 支持图片上传、拍照上传、裁剪、旋转、画笔和马赛克标注。
- 支持用户和 AI 发送图片/视频的缩略图、预览和保存。
- 支持 Markdown、表格、语法高亮代码块、LaTeX 数学公式和媒体 Data URL 渲染。
- 支持 AI 生成或返回的 base64、URL、本地媒体文件预览与另存。

### 主题与显示

- 支持浅色、深色和跟随系统主题。
- 支持 Material You 动态配色，只调整颜色，不改变应用布局。
- 支持字体大小调节、实时预览、跟随系统和更宽的自定义范围。
- 支持刷新率偏好设置：跟随系统智能刷新率、30 Hz、60 Hz、90 Hz、120 Hz。最终是否生效仍取决于设备屏幕和 Android 系统策略。

### 数据与备份

- 支持导出/导入个人资料、对话历史、模型配置、MCP、SSH、WebDAV、FTP/FTPS/SFTP、系统提示词、Skills 等。
- 支持本地 zip 备份和 WebDAV 云备份，补充模式会尽量去重并降低密钥被覆盖的风险。
- 支持不包含 API Key 的安全导出，也支持包含密钥的完整迁移备份。

### 用量统计

- 统计对话数、消息数、用户输入 Token 和 AI 输出 Token，思维链、工具上下文、文件读取、命令输出和重复上下文也计入对应统计。
- 使用离线 Token 估算工具，不依赖网络服务。
- 支持日、周、月、年、总计以及历史日期和时间段查询。


## 项目结构

```text
app/                         Android 应用模块
app/src/main/java/...        Kotlin / Jetpack Compose 源码
app/src/main/assets/textmate/ TextMate 语法、语言配置和配色主题
third_party/jlatexmath/      JLaTeXMath Android fork，用于 LaTeX 公式渲染
example-img/                 README 示例截图
gradle/                      Gradle Wrapper 配置
```

## 构建要求

- Android Studio 或命令行 Android SDK
- JDK 17
- Android SDK 36
- Gradle Wrapper

构建 Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

生成文件通常位于：

```text
app/build/outputs/apk/debug/
```

Release 包建议在 Android Studio 中手动配置签名后构建。请勿提交签名密钥、keystore、API Key、`.env`、`local.properties` 或任何本地隐私文件。

## Termux 配置

使用 `run_command` 前，需要在 Termux 中允许外部应用调用：

```bash
mkdir -p ~/.termux && (grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings
```

然后在 Lyra Code 的设置页面中授予 Termux 通信权限。未授权时，`run_command` 会自动禁用。

## 安全说明

Lyra Code 会处理 API Key、SSH 密码/私钥、MCP Token、WebDAV/FTP 凭据、对话内容、本地文件和远程服务器输出。请注意：

- 使用 HTTP 明文 API、MCP、WebDAV、FTP 或微型服务器服务时，数据可能被中间人读取。
- 将微型服务器暴露到局域网、内网穿透或公网时，如果目录、密码或 HTTPS 配置不当，可能泄露本地文件。启用外部访问前应检查工作区和认证配置。
- 让 AI 执行命令、修改文件、上传/下载文件或操作远程服务器前，应审查工具调用内容。
- 文件管理器需要 Android“管理所有文件”权限；确认批量操作前应仔细核对所选路径，并且只预览可信的本地 HTML，因为其中的脚本会在浏览器环境中执行。
- `run_command` 不再使用固定命令白名单，但仍会拦截 `rm -rf /`、写入 `/dev/block`、`mkfs` 等明显高危操作；执行前仍应人工审查。
- 联网搜索黑名单保存在本机设置中。普通域名为精确匹配：`x.com` 只拦截 `x.com`，`www.x.com` 需要单独添加。可使用 `*.x.com` 这类通配符拦截子域名；如果要同时拦截根域名和全部子域名，请同时填写 `x.com` 和 `*.x.com`。
- 包含密钥的备份文件应妥善保存，不要公开分享。
- 本项目不会替你判断远程脚本、MCP Server、Skills 仓库或 SSH 命令是否可信。

## PR 说明

本项目不接受外部 PR。若你有反馈或建议，请提交 Issue。若需要长期修改，请 Fork 本仓库自行维护，或联系仓库持有人。

## 许可证

本项目采用双重许可：Lyra Code 原创源代码可在 AGPLv3-or-later 下使用；如果需要闭源分发、私有修改、商业例外或不希望遵守 AGPL copyleft 义务，需要获取商业许可证。第三方组件仍以其各自许可证为准。

详见 [LICENSE](LICENSE) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
