package com.yukisoffd.lyracode

internal fun prootAgentToolCatalog(): List<AgentToolInfo> = listOf(
    AgentToolInfo(
        name = "proot_command",
        title = uiText(R.string.tool_proot_command),
        description = uiText(R.string.tool_proot_command_desc),
    ),
)
