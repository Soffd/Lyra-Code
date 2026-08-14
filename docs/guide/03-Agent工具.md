---
title: Agent 工具
---

## 5. Agent 工具体系


Agent 工具是 Lyra Code 的灵魂。所有工具都以「可审批、可禁用、可审计」的方式暴露给 AI。

### 5.1 工具分类

| 类别 | 代表工具 | 说明 |
| --- | --- | --- |
| 文件与目录 | list_directory / search_files / get_file_info / create_folder / rename_move / delete | 工作区内的原生操作 |
| 文件读写 | read_file / read_file_lines / write_file / edit_file / append_file | 支持精确行内编辑，自动生成 .bak |
| 全局存储 | global_* 系列 | Android 共享存储（/storage/emulated/0），工作区之外 |
| 下载 | download_file | 原生 HTTP/HTTPS，支持重定向、请求头、SHA-256 校验 |
| 命令 | run_command | Termux Shell，非交互命令 |
| 系统 | execute_shell_command / execute_root_command | 需 Shizuku / Root，另行授权 |
| 规划 | set_todo_list / update_todo_item | 多步任务的进度管理 |
| 联网 | web_search / read_web_page / mark_web_sources | 搜索、读页、来源标注 |
| 远程 | ssh_exec / list_emails / webdav_* / file_transfer_* | 各类远程服务器 |
| 定时 | manage_scheduled_tasks | 一次性 / 每日 / 每周 / 每月任务 |
| 备份 | export_backup / import_backup | 本地或 WebDAV |
| 其他 | get_current_time / get_current_location / get_device_hardware_info / list_installed_apps | 时间、位置、设备信息 |

### 5.2 工作区（Workspace）与全局存储的区别

```text
工作区：原生文件工具（read_file / edit_file 等）只能在所选工作区内操作，路径为相对路径。
全局：global_* 工具可访问整个 /storage/emulated/0（Android/data、Android/obb、/data 除外）。
```

- 默认工作区路径示例：`/storage/emulated/0/Lyra`，工具内使用相对路径如 `src/main.kt`；
- 不要向原生文件工具传 Termux 私有路径（如 `/data/data/com.termux`），那会被拒绝；
- `Download` 和 `Downloads` 均映射到 `/storage/emulated/0/Download`。

### 5.3 文件搜索

| 场景 | 工具 |
| --- | --- |
| 按文件名/路径片段找工作区文件 | search_files（query 只放文件名或片段，path 为 "." 或子目录） |
| 工作区外找文件 | 先 search_files，返回 SEARCH_EMPTY 再用 global_search_files 一次 |
| 按文件内容搜索 | 使用命令 `rg`（首选），缺失时退化为 `grep -r` |
| 远程存储找文件 | webdav_search / file_transfer_search |

::: warning 注意
`search_files` 只匹配文件名/路径，**不搜索文件内容**。
:::

### 5.4 原生下载

```text
download_file
  - url：HTTP/HTTPS 地址
  - path：目标路径（工作区相对路径，或全局路径）
  - headers：如 "Authorization: Bearer xxx"
  - sha256：可选完整性校验，不匹配则失败
```

优先使用 `download_file` 而不是 curl/wget——它有进度、重定向处理和校验能力。

### 5.5 TODO 规划

对于多步骤任务，AI 会创建 TODO 列表（3–7 项，状态为 pending / running / completed / blocked）：

- 简单对话、单次编辑、单一命令**不需要** TODO；
- 涉及多文件、多阶段、风险操作的任务必须有 TODO；
- 你可以在任意时刻要求 AI 展示或调整 TODO，强制它分步执行。

### 5.6 联网搜索与来源管理

- `web_search` 返回候选标题/URL/摘要，摘要只是线索；
- `read_web_page` 打开页面读取正文，**只信任真正读到的内容**；
- `mark_web_sources` 记录实际使用的来源，回答会附带链接；
- 网站黑名单：在设置中添加后，AI 无法打开被屏蔽域名（精确匹配，支持 `*.x.com` 通配符）。

### 5.7 审批与审计

每个可能改变状态的工具调用（写文件、执行命令、发邮件、远程操作）都会经过你的确认。

- 工具结果以结构化 JSON 返回（schema / ok / content / error / file_changes）；
- `ok=true` 只表示调用完成，命令是否成功要看 exit_code 和输出；
- 文件修改会附带 diff 供你审查；
- 所有工具均可在「Agent 工具页」单独禁用。

---

