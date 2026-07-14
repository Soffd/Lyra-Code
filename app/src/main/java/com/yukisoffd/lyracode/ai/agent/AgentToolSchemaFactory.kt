package com.yukisoffd.lyracode.ai

import android.util.Log
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale


internal class AgentToolSchemaFactory(
    private val settings: AppSettings,
    private val termuxExecutor: TermuxExecutor,
    private val systemCommandExecutor: SystemCommandExecutor,
) {
    fun toolDefinitions(allowSubAgents: Boolean = false): JSONArray {
        val definitions = JSONArray()
        .put(function("list_directory", "列出工作目录下的文件和子目录。path 必须是相对路径；根目录用 . 或空字符串。", "path" to "string"))
        .put(function("read_file", "读取工作目录内 1MB 以下文本文件。path 必须是相对路径，不要传 Termux 私有目录。", "path" to "string"))
        .put(
            functionWithOptional(
                "write_file",
                "写入或覆盖工作目录内文本文件。path 必须是相对路径，例如 test.py。代码或缩进敏感内容优先用 content_lines，每个数组元素是一行，应用会用 \\n 原样拼接，避免多空格、缩进或空行被压缩。",
                required = listOf("path" to "string"),
                optional = listOf("content" to "string", "content_lines" to "array:string", "ensure_trailing_newline" to "boolean"),
            ),
        )
        .put(
            functionWithOptional(
                "append_file",
                "追加文本到工作目录内文件末尾。缩进敏感内容优先用 content_lines，每个数组元素是一行，应用会用 \\n 原样拼接。",
                required = listOf("path" to "string"),
                optional = listOf("content" to "string", "content_lines" to "array:string", "ensure_trailing_newline" to "boolean"),
            ),
        )
        .put(function("create_folder", "在工作目录内创建目录。path 必须是相对路径。", "path" to "string"))
        .put(function("delete_file_or_folder", "删除工作目录内文件或空目录。path 必须是相对路径。", "path" to "string"))
        .put(function("rename_move", "同目录重命名", "from" to "string", "to" to "string"))
        .put(function("global_list_directory", "列出 Android 共享存储中的文件和子目录。用于非工作区文件，path 可填 Download、Downloads、相对共享存储路径或 /storage/emulated/0 下路径。禁止访问 Android/data、Android/obb 和 /data。", "path" to "string"))
        .put(function("global_read_file", "读取 Android 共享存储内 1MB 以下文本文件。用于读取 Download 目录备份说明或非工作区文本文件。", "path" to "string"))
        .put(
            functionWithOptional(
                "global_write_file",
                "写入或覆盖 Android 共享存储内文本文件。执行前会请求用户确认。缩进敏感内容优先用 content_lines。",
                required = listOf("path" to "string"),
                optional = listOf("content" to "string", "content_lines" to "array:string", "ensure_trailing_newline" to "boolean"),
            ),
        )
        .put(
            functionWithOptional(
                "global_append_file",
                "追加文本到 Android 共享存储内文件末尾。执行前会请求用户确认。缩进敏感内容优先用 content_lines。",
                required = listOf("path" to "string"),
                optional = listOf("content" to "string", "content_lines" to "array:string", "ensure_trailing_newline" to "boolean"),
            ),
        )
        .put(function("global_create_folder", "在 Android 共享存储内创建目录。执行前会请求用户确认。", "path" to "string"))
        .put(function("global_delete_file_or_folder", "删除 Android 共享存储内文件或目录。执行前会请求用户确认。", "path" to "string"))
        .put(function("global_rename_move", "移动或重命名 Android 共享存储内文件或目录。执行前会请求用户确认。", "from" to "string", "to" to "string"))
        .put(
            functionWithOptional(
                "download_file",
                "使用应用原生 HTTP/HTTPS 客户端下载文件，不依赖 Termux。必须优先于 curl/wget 使用。destination=workspace 时 path 为工作区相对路径；若未选择工作区，应用会自动保存到 Android 共享存储 Download/LyraCode/<path>。destination=global 时 path 为 Android 共享存储路径，例如 Download/file.zip。执行前会请求用户确认。headers 每项使用 Name: Value 格式；sha256 可用于完整性校验。",
                required = listOf("url" to "string", "path" to "string"),
                optional = listOf(
                    "destination" to "string",
                    "headers" to "array:string",
                    "sha256" to "string",
                    "timeout_seconds" to "integer",
                ),
            ),
        )
        .put(
            functionWithOptional(
                "manage_scheduled_tasks",
                "管理 Lyra Code 后台定时任务。action=list 可查看任务；create/update/delete/enable/disable 会先请求用户确认。schedule_type 支持 once、daily、weekly、monthly。once 使用 run_at；daily 使用 hour/minute；weekly 额外使用 day_of_week（1=周一，7=周日）；monthly 额外使用 day_of_month。每个任务可指定独立 profile_id 和 model，不会出现在普通历史会话中。",
                required = listOf("action" to "string"),
                optional = listOf(
                    "task_id" to "string",
                    "title" to "string",
                    "prompt" to "string",
                    "schedule_type" to "string",
                    "run_at" to "string",
                    "hour" to "integer",
                    "minute" to "integer",
                    "day_of_week" to "integer",
                    "day_of_month" to "integer",
                    "profile_id" to "string",
                    "model" to "string",
                    "enabled" to "boolean",
                ),
            ),
        )
        .put(function("get_mini_server_status", "读取 Lyra Code 微型服务器运行状态、当前工作区、监听地址、本机 URL 和局域网访问 URL。"))
        .put(
            functionWithOptional(
                "read_mini_server_logs",
                "读取 Lyra Code 微型服务器最近终端日志，包括连接、资源加载、404、认证失败和页面 JavaScript 报错。用于调试本地静态站点、Vue/Vite/VitePress/HTML/CSS/JS 页面问题。level 可选 debug/info/warn/error；limit 最大 500。",
                required = emptyList(),
                optional = listOf("limit" to "integer", "level" to "string"),
            ),
        )
        .put(
            functionWithOptional(
                "manage_mini_server",
                "启动、停止、重启或更新 Lyra Code 内置微型 HTTP/HTTPS 静态服务器。服务器以当前工作区作为站点根目录，可用于 Vue/Vite/VitePress/HTML/CSS/JS 静态站点调试。action=status/update/start/stop/restart/reset；host=127.0.0.1 仅本机，0.0.0.0 面向局域网/公网映射；username/password 用于 Basic 认证，password 为空表示不启用认证。HTTPS 支持 tls_key_store_base64/tls_key_store_password 或 tls_certificate_chain/tls_private_key；force_https 会把 HTTP 请求重定向到 HTTPS。",
                required = listOf("action" to "string"),
                optional = listOf(
                    "protocol" to "string",
                    "host" to "string",
                    "port" to "integer",
                    "username" to "string",
                    "password" to "string",
                    "custom_domains" to "array:string",
                    "force_https" to "boolean",
                    "tls_key_store_base64" to "string",
                    "tls_key_store_password" to "string",
                    "tls_certificate_chain" to "string",
                    "tls_private_key" to "string",
                    "spa_fallback" to "boolean",
                    "directory_listing" to "boolean",
                    "mdns_enabled" to "boolean",
                    "mdns_name" to "string",
                ),
            ),
        )
        .put(
            functionWithOptional(
                "search_conversation_history",
                "跨普通会话搜索历史记录，可按关键词和时间段筛选。返回会话 id、标题、时间、消息数和简短预览；不会返回思维链或工具调用内容。start_time/end_time 可用时间戳、yyyy-MM-dd、yyyy-MM-dd HH:mm 或 ISO-8601。",
                required = emptyList(),
                optional = listOf("query" to "string", "start_time" to "string", "end_time" to "string", "limit" to "integer"),
            ),
        )
        .put(
            functionWithOptional(
                "read_conversation_history",
                "读取一个或多个普通历史会话的用户消息和 AI 最终可见回复。不会传入思维链、工具调用请求或工具返回，适合总结近期工作、生成周报。先用 search_conversation_history 获取 id。",
                required = emptyList(),
                optional = listOf("conversation_id" to "string", "conversation_ids" to "array:string", "max_messages" to "integer"),
            ),
        )
        .put(function("search_files", "在工作目录内按文件名、扩展名或路径片段搜索文件。查找文件路径时必须优先使用此工具；query 填文件名或关键词，path 填 . 或相对子目录。", "query" to "string", "path" to "string"))
        .put(function("global_search_files", "在 Android 共享存储 /storage/emulated/0 下按文件名或路径片段全局搜索文件。仅当 search_files 返回 SEARCH_EMPTY 且用户需要查找工作区外文件时调用一次；不要用它替代工作区内搜索。返回绝对路径。", "query" to "string"))
        .put(function("get_file_info", "获取文件元数据", "path" to "string"))
        .put(function("list_skill_files", "列出已启用 Skill 包内文件。先根据 LYRA_ACTIVE_SKILLS_V1 判断相关 Skill，再调用此工具。", "skill_id" to "string"))
        .put(function("read_skill_file", "读取指定 Skill 包内文本文件。优先读取 SKILL.md；只读取和当前任务相关的文件。", "skill_id" to "string", "path" to "string"))
        if (termuxExecutor.hasRunCommandPermission()) {
            definitions.put(
                functionWithOptional(
                    "run_command",
                    "在 Termux 中执行 Shell 命令，并直接返回 exit_code、stdout、stderr；仅明显高风险命令会被拦截。下载文件必须优先使用 download_file；仅当原生下载明确失败、被禁用或不支持目标协议时，才把 curl/wget 作为最后备用手段。不要运行不会退出的长期驻留命令；多行脚本或缩进敏感命令优先用 command_lines，每个数组元素是一行，应用会用 \\n 原样拼接。默认等待 60 秒；确实需要更久时传 timeout_seconds，最大 600。",
                    required = emptyList(),
                    optional = listOf("command" to "string", "command_lines" to "array:string", "workDir" to "string", "timeout_seconds" to "integer"),
                ),
            )
        }
        definitions
        .put(function("web_search", "使用内嵌 WebView 搜索互联网，返回候选网页标题、URL 和摘要。会自动过滤用户设置的网站黑名单；需要最新信息或网页资料时先调用。", "query" to "string", "limit" to "integer"))
        .put(function("read_web_page", "使用内嵌 WebView 打开并读取 http/https 网页正文。会拒绝读取用户设置的网站黑名单域名；应在 web_search 后读取可信候选网页，再基于网页内容回答。", "url" to "string"))
        .put(function("mark_web_sources", "网页来源标注工具。只在回答依赖网页内容时调用；sources 为数组，每项包含 title、url、used_for。调用后最终回答必须在相应结论旁使用 Markdown 链接标注来源。", "sources" to "array"))
        .put(
            functionWithOptional(
                "manage_app_config",
                "配置管理工具。用户要求通过自然语言添加、修改、启用、禁用、删除 MCP 服务器、SSH 连接、WebDAV、FTP/FTPS/SFTP 文件传输服务器、Skills 或其他 Agent 工具时调用。若用户要启用已禁用配置或工具但名称不明确，先用 target=all action=list 查看 disabled_summary。支持从网页读取到的 MCP JSON/Skill zip URL 自动落库；需要额外 key/密码时先向用户索取。除 manage_app_config 自身外，agent 工具只能启用/禁用，不能删除。",
                required = listOf("target" to "string", "action" to "string"),
                optional = listOf(
                    "id" to "string",
                    "name" to "string",
                    "description" to "string",
                    "url" to "string",
                    "raw_json" to "string",
                    "auth_key" to "string",
                    "transport" to "string",
                    "timeout_seconds" to "integer",
                    "host" to "string",
                    "port" to "integer",
                    "username" to "string",
                    "password" to "string",
                    "private_key" to "string",
                    "passphrase" to "string",
                    "auth_type" to "string",
                    "protocol" to "string",
                    "use_private_key" to "boolean",
                    "encoding" to "string",
                    "passive_mode" to "boolean",
                    "explicit_ftps" to "boolean",
                    "sync_permissions" to "boolean",
                    "zip_url" to "string",
                    "tool_name" to "string",
                    "user_agent" to "string",
                    "initial_path" to "string",
                    "path" to "string",
                    "note" to "string",
                    "trust_all_certificates" to "boolean",
                    "multi_thread" to "boolean",
                    "hide_address" to "boolean",
                    "enabled" to "boolean",
                ),
            ),
        )
        .put(function("get_current_time", "读取设备当前本地时间、时区和时间戳。需要判断今天、近期、搜索时间范围或个性化回答时调用。"))
        .put(function("get_current_location", "读取设备最近一次系统定位。需要按用户所在地区个性化回答或联网搜索地区相关信息时调用；若未授权会返回权限状态。"))
        .put(function("get_device_hardware_info", "硬件检查工具。读取当前 Android 设备的系统、CPU、内存、存储、ABI、分辨率、网络、蓝牙、电池等诊断信息。用于机型问题排查、判断设备硬件是否异常、山寨机线索分析、购机性价比比较等；不要把结果视为绝对鉴定结论。"))
        .put(
            functionWithOptional(
                "list_installed_apps",
                "读取设备已安装应用列表，返回应用名、包名、版本、APK 大小、用户/系统应用分类及签名证书 SHA-256。scope 可用 all、user、system；应用很多时使用 offset 和 limit 分页。适合排查软件版本、包名、签名或可疑应用。",
                required = emptyList(),
                optional = listOf("scope" to "string", "query" to "string", "offset" to "integer", "limit" to "integer"),
            ),
        )
        if (systemCommandExecutor.shouldShowShellTool()) {
            definitions.put(
                functionWithOptional(
                    "execute_shell_command",
                    "通过 Shizuku 以 Android shell 身份执行系统命令并返回 exit_code、stdout、stderr。每次执行都需要用户确认。可用于 pm list/enable/disable-user/install/uninstall、cmd、dumpsys 和受 shell 权限保护的 /data 路径；先用只读命令检查状态，再执行变更。禁止运行不会退出的交互程序。",
                    required = emptyList(),
                    optional = listOf("command" to "string", "command_lines" to "array:string", "timeout_seconds" to "integer"),
                ),
            )
        }
        if (systemCommandExecutor.shouldShowRootTool()) {
            definitions.put(
                functionWithOptional(
                    "execute_root_command",
                    "通过用户配置的 su 命令以 Root 身份执行系统命令并返回 exit_code、stdout、stderr。每次执行都需要用户确认。执行卸载系统组件、修改 /data 或系统文件前必须先检查目标和备份方案；Root 不可用时仅在 Shell 开关也开启且已授权时回退到 Shizuku Shell。",
                    required = emptyList(),
                    optional = listOf("command" to "string", "command_lines" to "array:string", "timeout_seconds" to "integer"),
                ),
            )
        }
        definitions
        .put(function("list_ssh_servers", "列出用户已配置且启用的 SSH 服务器。调用 ssh_exec 前必须先调用本工具，使用返回的 id（通常是 host:port）作为 server_id。"))
        .put(
            functionWithOptional(
                "ssh_exec",
                "通过 SSH 登录用户配置的远程 Linux/Windows/Git 服务器执行命令并返回 exit_code/stdout/stderr。server_id 必须来自 list_ssh_servers。执行安装、修改配置、启动服务前必须先检查系统、CPU/GPU、内存、磁盘，例如 uname/systeminfo、free、df、lscpu/nvidia-smi。禁止直接读取 /var/log 或 *.log；需要先用 ls/stat/du/wc -l 查看属性，再读取很小片段。不要运行 vim/top/ssh 等复杂交互 shell；简单 Y/N 可用 input_lines。",
                required = listOf("server_id" to "string"),
                optional = listOf("command" to "string", "command_lines" to "array:string", "cwd" to "string", "input_lines" to "array:string", "timeout_seconds" to "integer"),
            ),
        )
        .put(function("list_webdav_servers", "列出用户已配置且启用的 WebDAV 服务器。调用 WebDAV 搜索、上传、下载或云备份前必须先调用本工具，使用返回的 id 作为 server_id。"))
        .put(
            functionWithOptional(
                "webdav_list",
                "使用 PROPFIND 列出指定 WebDAV 目录下的文件和子目录详情，返回路径、是否目录、大小、修改时间。需要浏览服务器目录、文件名未知、搜索不到文件或确认目录结构时优先调用；只读取元数据，不下载文件。",
                required = listOf("server_id" to "string"),
                optional = listOf("path" to "string", "depth" to "integer"),
            ),
        )
        .put(
            functionWithOptional(
                "webdav_search",
                "在指定 WebDAV 服务器中按文件名或路径片段搜索文件。只返回路径和元数据，不会下载文件。文件名未知或需要列目录时不要搜索 . 取巧，应调用 webdav_list。",
                required = listOf("server_id" to "string", "query" to "string"),
                optional = listOf("path" to "string", "limit" to "integer"),
            ),
        )
        .put(function("webdav_download_to_workspace", "从 WebDAV 下载文件到当前工作区。必须先获得用户确认；local_path 必须是工作区相对路径。", "server_id" to "string", "remote_path" to "string", "local_path" to "string"))
        .put(function("webdav_upload_from_workspace", "把当前工作区文件上传到 WebDAV。必须先获得用户确认；local_path 必须是工作区相对路径。", "server_id" to "string", "local_path" to "string", "remote_path" to "string"))
        .put(function("list_file_transfer_servers", "列出用户已配置且启用的 FTP/FTPS/SFTP 文件传输服务器。调用文件传输搜索、上传、下载前必须先调用本工具，使用返回的 id 作为 server_id。"))
        .put(
            functionWithOptional(
                "file_transfer_list",
                "列出指定 FTP/FTPS/SFTP 目录下的文件和子目录详情，返回路径、是否目录、大小、修改时间。文件名未知、搜索不到文件或确认目录结构时优先调用；只读取元数据，不下载文件。",
                required = listOf("server_id" to "string"),
                optional = listOf("path" to "string"),
            ),
        )
        .put(
            functionWithOptional(
                "file_transfer_search",
                "在指定 FTP/FTPS/SFTP 服务器中按文件名或路径片段搜索文件。只返回路径和元数据，不会下载文件。",
                required = listOf("server_id" to "string", "query" to "string"),
                optional = listOf("path" to "string", "limit" to "integer"),
            ),
        )
        .put(function("file_transfer_download_to_workspace", "从 FTP/FTPS/SFTP 下载文件到当前工作区。必须先获得用户确认；local_path 必须是工作区相对路径。", "server_id" to "string", "remote_path" to "string", "local_path" to "string"))
        .put(function("file_transfer_upload_from_workspace", "把当前工作区文件上传到 FTP/FTPS/SFTP。必须先获得用户确认；local_path 必须是工作区相对路径。", "server_id" to "string", "local_path" to "string", "remote_path" to "string"))
        .put(
            functionWithOptional(
                "export_backup",
                "导出 Lyra Code 备份到 Download/LyraCode 或 WebDAV。包含密钥时必须提醒用户妥善保管；destination 为 local 或 webdav。WebDAV 未指定 remote_path 时默认覆盖 /LyraCode/lyra_backup_latest.zip，便于下次直接导入。",
                required = listOf("destination" to "string"),
                optional = listOf(
                    "server_id" to "string",
                    "remote_path" to "string",
                    "include_profile" to "boolean",
                    "include_conversations" to "boolean",
                    "include_model_profiles" to "boolean",
                    "include_mcp" to "boolean",
                    "include_ssh" to "boolean",
                    "include_prompts" to "boolean",
                    "include_skills" to "boolean",
                    "include_webdav" to "boolean",
                    "include_file_transfer" to "boolean",
                    "include_secrets" to "boolean",
                ),
            ),
        )
        .put(
            functionWithOptional(
                "import_backup",
                "从工作区本地 zip、Android Download/共享存储 zip 或 WebDAV zip 导入 Lyra Code 备份。Agent 固定使用补充模式导入并去重，不允许覆盖模式。source 可用 local、download、global、webdav；download/global 使用 global_path 或 local_path。WebDAV 的 remote_path 可留空，应用会优先导入 /LyraCode/lyra_backup_latest.zip，找不到则自动选择 /LyraCode 下最新的 Lyra backup zip。",
                required = listOf("source" to "string"),
                optional = listOf("server_id" to "string", "remote_path" to "string", "local_path" to "string", "global_path" to "string"),
            ),
        )
        if (allowSubAgents && settings.subAgentOrchestrationEnabled && settings.enabledSubAgents().isNotEmpty()) {
            definitions.put(
                function(
                    "run_sub_agents",
                    "子代理编排工具。仅在用户任务复杂、需要并行研究、独立代码审查、多方案验证或跨领域分工时调用。tasks 为数组，每项包含 task、capability_hint、expected_output，可选 sub_agent_id/agent/model 指定目标子代理；未指定时系统会按能力匹配并在无明显匹配时均衡分配到不同启用模型。子代理会独立调用可用工具并请求必要审批；完成后只返回最终结果，不返回 thinking。",
                    "tasks" to "array",
                ),
            )
        }
        definitions
        .put(function("set_todo_list", "设置当前任务 TODO 列表。修改文件或执行命令前必须先调用。items 为数组，每项包含 id、text、status、note。", "items" to "array"))
        .put(function("update_todo_item", "更新 TODO 项状态。status 可用 pending、running、completed、blocked。", "id" to "string", "status" to "string", "note" to "string"))
        settings.enabledMcpTools().forEach { (server, tool) ->
            runCatching { mcpFunction(server, tool) }
                .onSuccess { definitions.put(it) }
                .onFailure {
                    Log.w(AGENT_TAG, "skip_invalid_mcp_schema server=${server.name} tool=${tool.name} error=${it.message}", it)
                }
        }
        val disabled = settings.disabledTools()
        return JSONArray().apply {
            for (index in 0 until definitions.length()) {
                val item = definitions.getJSONObject(index)
                val name = item.optJSONObject("function")?.optString("name").orEmpty()
                if (name == "manage_app_config" || name !in disabled) put(item)
            }
        }
    }

    fun anthropicTools(allowSubAgents: Boolean = false): JSONArray {
        val tools = toolDefinitions(allowSubAgents)
        return JSONArray().also { output ->
            for (index in 0 until tools.length()) {
                val function = tools.optJSONObject(index)?.optJSONObject("function") ?: continue
                output.put(
                    JSONObject()
                        .put("name", function.optString("name"))
                        .put("description", function.optString("description"))
                        .put("input_schema", function.optJSONObject("parameters") ?: JSONObject().put("type", "object")),
                )
            }
        }
    }

    fun geminiFunctionDeclarations(allowSubAgents: Boolean = false): JSONArray {
        val tools = toolDefinitions(allowSubAgents)
        return JSONArray().also { output ->
            for (index in 0 until tools.length()) {
                val function = tools.optJSONObject(index)?.optJSONObject("function") ?: continue
                output.put(
                    JSONObject()
                        .put("name", function.optString("name"))
                        .put("description", function.optString("description"))
                        .put("parameters", toGeminiSchema(function.optJSONObject("parameters") ?: JSONObject().put("type", "object"))),
                )
            }
        }
    }

    private fun toGeminiSchema(source: JSONObject): JSONObject {
        val output = JSONObject()
        val type = source.optString("type").ifBlank { "object" }
        output.put("type", type.uppercase(Locale.US))
        source.stringFieldOrNull("description")?.let { output.put("description", it) }
        source.optJSONArray("required")?.let { output.put("required", it) }
        source.optJSONArray("enum")?.let { output.put("enum", it) }
        source.optJSONObject("properties")?.let { props ->
            val outProps = JSONObject()
            props.keys().forEach { name ->
                (props.optJSONObject(name) ?: JSONObject().put("type", "string")).let { outProps.put(name, toGeminiSchema(it)) }
            }
            output.put("properties", outProps)
        }
        source.optJSONObject("items")?.let { output.put("items", toGeminiSchema(it)) }
        return output
    }

    private fun function(name: String, description: String, vararg properties: Pair<String, String>): JSONObject {
        return functionWithOptional(name, description, required = properties.toList(), optional = emptyList())
    }

    private fun mcpFunction(server: McpServerConfig, tool: McpToolDefinition): JSONObject {
        val parameters = runCatching { JSONObject(tool.inputSchema.ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
        val sanitized = sanitizeMcpSchema(parameters) as? JSONObject ?: JSONObject()
        if (sanitized.optString("type").isBlank()) {
            sanitized.put("type", "object")
        }
        if (!sanitized.has("properties")) {
            sanitized.put("properties", JSONObject())
        }
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", settings.mcpToolFunctionName(server, tool))
                    .put(
                        "description",
                        "MCP:${server.name} / ${tool.name}. ${tool.description}".take(1024),
                    )
                    .put("parameters", sanitized),
            )
    }

    private fun sanitizeMcpSchema(value: Any?): Any {
        return when (value) {
            is JSONObject -> sanitizeMcpSchemaObject(value)
            is JSONArray -> JSONArray().also { array ->
                for (index in 0 until value.length()) {
                    sanitizeMcpSchema(value.opt(index)).let { sanitized ->
                        if (!isNullSchema(sanitized)) array.put(sanitized)
                    }
                }
            }
            is Boolean -> JSONObject()
            JSONObject.NULL, null -> JSONObject()
            else -> value
        }
    }

    private fun sanitizeMcpSchemaObject(source: JSONObject): JSONObject {
        val output = JSONObject()
        source.keys().forEach { key ->
            when (key) {
                "type" -> normalizeJsonSchemaType(source.opt(key))?.let { output.put("type", it) }
                "properties" -> {
                    val props = source.optJSONObject(key) ?: JSONObject()
                    val sanitizedProps = JSONObject()
                    props.keys().forEach { propName ->
                        sanitizedProps.put(propName, sanitizeMcpSchema(props.opt(propName)))
                    }
                    output.put("properties", sanitizedProps)
                }
                "items", "additionalProperties" -> output.put(key, sanitizeMcpSchema(source.opt(key)))
                "anyOf", "oneOf", "allOf" -> {
                    val sourceArray = source.optJSONArray(key)
                    val sanitizedArray = JSONArray()
                    if (sourceArray != null) {
                        for (index in 0 until sourceArray.length()) {
                            val item = sanitizeMcpSchema(sourceArray.opt(index))
                            if (!isNullSchema(item)) sanitizedArray.put(item)
                        }
                    }
                    if (sanitizedArray.length() == 1) {
                        val only = sanitizedArray.optJSONObject(0)
                        if (only != null) {
                            only.keys().forEach { innerKey -> output.put(innerKey, only.opt(innerKey)) }
                        } else {
                            output.put(key, sanitizedArray)
                        }
                    } else if (sanitizedArray.length() > 1) {
                        output.put(key, sanitizedArray)
                    }
                }
                "required" -> {
                    val required = source.optJSONArray(key) ?: JSONArray()
                    val sanitizedRequired = JSONArray()
                    for (index in 0 until required.length()) {
                        required.optString(index).takeIf { it.isNotBlank() }?.let { sanitizedRequired.put(it) }
                    }
                    output.put("required", sanitizedRequired)
                }
                "enum" -> output.put(key, source.optJSONArray(key) ?: JSONArray())
                "description", "title", "default", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems", "pattern" -> {
                    output.put(key, source.opt(key))
                }
                else -> {
                    val raw = source.opt(key)
                    if (raw is JSONObject || raw is JSONArray) output.put(key, sanitizeMcpSchema(raw))
                }
            }
        }
        return output
    }

    private fun normalizeJsonSchemaType(raw: Any?): Any? {
        fun normalizeOne(type: String): String? {
            return when (type.trim().lowercase()) {
                "bool", "boolean" -> "boolean"
                "str", "string", "text" -> "string"
                "int", "integer" -> "integer"
                "float", "double", "number" -> "number"
                "dict", "map", "object" -> "object"
                "list", "array" -> "array"
                "null", "none", "nil" -> null
                else -> if (type in JSON_SCHEMA_TYPES) type else "string"
            }
        }
        return when (raw) {
            is String -> normalizeOne(raw)
            is JSONArray -> {
                val array = JSONArray()
                for (index in 0 until raw.length()) {
                    raw.optString(index).takeIf { it.isNotBlank() }?.let { normalizeOne(it) }?.let { array.put(it) }
                }
                when (array.length()) {
                    0 -> null
                    1 -> array.optString(0)
                    else -> array
                }
            }
            else -> null
        }
    }

    private fun isNullSchema(value: Any?): Boolean {
        return value is JSONObject && value.optString("type").equals("null", ignoreCase = true)
    }

    private fun functionWithOptional(
        name: String,
        description: String,
        required: List<Pair<String, String>>,
        optional: List<Pair<String, String>>,
    ): JSONObject {
        val props = JSONObject()
        val requiredArray = JSONArray()
        (required + optional).forEach { (key, type) ->
            val schema = when (type) {
                "array:string" -> JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject().put("type", "string"))
                "array:object" -> JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject().put("type", "object"))
                else -> JSONObject().put("type", type)
            }
            props.put(key, schema)
        }
        required.forEach { (key, _) -> requiredArray.put(key) }
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", requiredArray)),
            )
    }


    private fun JSONObject.stringFieldOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name) ?: return null
        val text = value as? String ?: return null
        return text.takeUnless { it.equals("null", ignoreCase = true) }
    }

    private companion object {
        const val AGENT_TAG = "LyraAgent"
        val JSON_SCHEMA_TYPES = setOf("string", "number", "integer", "boolean", "object", "array")
    }}

