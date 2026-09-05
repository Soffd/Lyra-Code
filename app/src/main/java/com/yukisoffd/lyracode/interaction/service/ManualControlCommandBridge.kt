package com.yukisoffd.lyracode.interaction.service

/** Process-local bridge; the foreground overlay never keeps an AccessibilityService reference. */
internal object ManualControlCommandBridge {
    @Volatile
    private var confirmAction: (() -> Unit)? = null

    fun attach(confirmAction: () -> Unit) {
        this.confirmAction = confirmAction
    }

    fun detach() {
        confirmAction = null
    }

    fun requestConfirm() {
        confirmAction?.invoke()
    }
}
