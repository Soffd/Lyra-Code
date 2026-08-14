---
title: 远程集成工具
---

所有远程工具统一模式：**先列账号，再操作**。

## 账号管理（manage_app_config）

- 管理 MCP、SSH、邮件、WebDAV、FTP/FTPS/SFTP 配置；
- 缺失的 Key/密码/应用专用密码/私钥必须由你提供，AI 不会凭空捏造；
- 身份不明先 list，按返回 id 操作；凭据变更后重新 list。

## SSH

```text
list_ssh_servers → ssh_exec(server_id, command)
```

- 支持密码/密钥登录 Linux / Windows / Git 服务器；
- 每次执行需确认；远程装软件/改配置前先检查系统、资源与权限；
- 读日志先看大小：`ls -lh app.log && tail -50 app.log`；
- 避免交互式程序（vim、top、嵌套 ssh）；分步确认，不一条龙串联。

## 邮件（IMAP / SMTP）

```text
list_email_accounts → list_email_folders → list_emails → read_email / save_email_draft / send_email
```

- 读信不改状态（不会标记已读）；正文只返回文本，省略媒体与附件字节；
- 附件只下载到**隔离目录**，AI 不读取内容，请杀毒后再打开；
- 发送（send_email）每次单独确认，不自动重试可能重复的投递；
- 更稳妥：存草稿（save_email_draft）审阅后再发；
- 不要回复当前配置账号自己发来的邮件。

## WebDAV

```text
list_webdav_servers → webdav_list / webdav_search / webdav_download_to_workspace / webdav_upload_from_workspace
```

- 优先 `webdav_list`（PROPFIND）看目录结构；
- 支持云备份（export_backup destination=webdav）。

## FTP / FTPS / SFTP

```text
list_file_transfer_servers → file_transfer_list / file_transfer_search / 上传 / 下载
```

支持列目录、搜索、上传、下载；凭据与被动模式在配置中维护。

## MCP

**客户端**：连接远程 MCP Server（Streamable HTTP / SSE），工具运行在外部服务器上，不自动获得工作区访问权，每次调用需批准。

**服务端**：把启用的本地工具与已连接 MCP 工具通过 HTTP 暴露给其他客户端，可设端口与认证 Key。

::: danger 暴露风险
暴露到局域网/公网前务必设置认证 Key；MCP 工具在外部服务器上运行，其可信性由你判断。
:::