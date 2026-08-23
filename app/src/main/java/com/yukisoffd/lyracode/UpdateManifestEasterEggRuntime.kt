package com.yukisoffd.lyracode

import android.content.Context

/** Process-local hand-off for the one intentional crash in the easter-egg sequence. */
internal object UpdateManifestEasterEggRuntime {
    private const val PREFERENCES = "update_manifest_easter_egg"
    private const val KEY_INTENTIONAL_CRASH_CHECKPOINT = "intentional_crash_checkpoint"
    private const val KEY_COMPLETED = "completed"

    private var initialized = false
    private var resumeClickCount = 0
    private var stoppedForCurrentProcess = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val preferences = preferences(context)
        val resumeAfterCrash = preferences.getBoolean(KEY_INTENTIONAL_CRASH_CHECKPOINT, false)
        if (resumeAfterCrash) {
            // Consume before any UI is shown. A second process death therefore resets the attempt.
            preferences.edit().remove(KEY_INTENTIONAL_CRASH_CHECKPOINT).commit()
            resumeClickCount = UPDATE_MANIFEST_EASTER_EGG_RESUME_COUNT
        }
        initialized = true
    }

    @Synchronized
    fun takeSession(context: Context): UpdateManifestEasterEggSession {
        initialize(context)
        val initialClickCount = resumeClickCount
        resumeClickCount = 0
        return UpdateManifestEasterEggSession(initialClickCount).also { session ->
            if (stoppedForCurrentProcess) session.chooseStop()
        }
    }

    @Synchronized
    fun stopForCurrentProcess() {
        stoppedForCurrentProcess = true
        resumeClickCount = 0
    }

    @Synchronized
    fun beginFreshAppTask() {
        stoppedForCurrentProcess = false
    }

    fun prepareIntentionalCrash(context: Context) {
        preferences(context)
            .edit()
            .putBoolean(KEY_INTENTIONAL_CRASH_CHECKPOINT, true)
            .commit()
    }

    fun isCompleted(context: Context): Boolean = preferences(context).getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_INTENTIONAL_CRASH_CHECKPOINT)
            .putBoolean(KEY_COMPLETED, true)
            .commit()
    }

    @Synchronized
    fun clear(context: Context) {
        resumeClickCount = 0
        stoppedForCurrentProcess = false
        preferences(context).edit().clear().commit()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )
}

internal class IntentionalUpdateManifestCrashException : RuntimeException(
    "Intentional one-shot update-manifest easter-egg crash",
)
