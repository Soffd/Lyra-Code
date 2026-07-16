package com.yukisoffd.lyracode.ai

data class AgentFileMutation(
    val path: String,
    val content: String,
    val globalStorage: Boolean,
    val beforeContent: String? = null,
    val editorApplied: Boolean = false,
)

data class AgentFileEditResult(
    val handled: Boolean,
    val applied: Boolean,
    val message: String = "",
) {
    companion object {
        val NotHandled = AgentFileEditResult(handled = false, applied = false)
        val Applied = AgentFileEditResult(handled = true, applied = true)
        fun failed(message: String) = AgentFileEditResult(handled = true, applied = false, message = message)
    }
}

data class AgentFileActivity(
    val path: String,
    val globalStorage: Boolean,
    val operation: String,
    val content: String? = null,
)
