package com.yukisoffd.lyracode.ai

import org.json.JSONArray
import org.json.JSONObject

internal fun prootCommandToolDefinition(): JSONObject = JSONObject()
    .put("type", "function")
    .put(
        "function",
        JSONObject()
            .put("name", "proot_command")
            .put(
                "description",
                "Run a foreground shell command inside one of Lyra Code's installed PRoot Linux environments. " +
                    "linux_id selects the environment. A selected workspace is mounted at /workspace; without one the default working directory is /root. " +
                    "When Android All files access is granted, Android shared storage is mounted under /storage and primary storage is also available at /sdcard. " +
                    "workDir may be workspace-relative, a mounted shared-storage path, or an absolute path inside Linux. This tool does not use or require Termux. " +
                    "Use command_lines for multiline or indentation-sensitive commands. Timeout defaults to 60 seconds and is limited to 600 seconds.",
            )
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("linux_id", JSONObject().put("type", "string"))
                            .put("command", JSONObject().put("type", "string"))
                            .put(
                                "command_lines",
                                JSONObject()
                                    .put("type", "array")
                                    .put("items", JSONObject().put("type", "string")),
                            )
                            .put(
                                "workDir",
                                JSONObject()
                                    .put("type", "string")
                                    .put(
                                        "description",
                                        "Optional working directory. Relative paths use /workspace when selected, otherwise /root. " +
                                            "Use /workspace/..., /sdcard/..., /storage/emulated/0/..., or an absolute Linux path as appropriate.",
                                    ),
                            )
                            .put("timeout_seconds", JSONObject().put("type", "integer")),
                    )
                    .put("required", JSONArray().put("linux_id")),
            ),
    )
