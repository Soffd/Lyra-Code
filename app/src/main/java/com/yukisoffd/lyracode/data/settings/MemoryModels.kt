package com.yukisoffd.lyracode.data

data class MemoryEntry(
    val id: String,
    val content: String,
    val category: String = CATEGORY_OTHER,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    companion object {
        const val CATEGORY_PREFERENCE = "preference"
        const val CATEGORY_WORK_STYLE = "work_style"
        const val CATEGORY_COMMUNICATION = "communication"
        const val CATEGORY_PERSONAL = "personal"
        const val CATEGORY_OTHER = "other"

        val categories = listOf(
            CATEGORY_PREFERENCE,
            CATEGORY_WORK_STYLE,
            CATEGORY_COMMUNICATION,
            CATEGORY_PERSONAL,
            CATEGORY_OTHER,
        )

        fun normalizeCategory(value: String): String {
            val normalized = value.trim().lowercase()
            return normalized.takeIf { it in categories } ?: CATEGORY_OTHER
        }
    }
}
