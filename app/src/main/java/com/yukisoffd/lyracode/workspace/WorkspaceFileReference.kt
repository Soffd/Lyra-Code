package com.yukisoffd.lyracode.workspace

data class WorkspaceFileReference(
    val name: String,
    val relativePath: String,
    val uri: String,
    val size: Long = 0L,
    val directory: Boolean = false,
    val modifiedAt: Long = 0L,
)
