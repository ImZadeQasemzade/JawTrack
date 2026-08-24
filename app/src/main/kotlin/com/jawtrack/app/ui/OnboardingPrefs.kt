package com.jawtrack.app.ui

import android.content.Context

/** Tracks whether the one-time setup flow (§4.2, §4.6.1, §6.8) has been completed. */
object OnboardingPrefs {
    private const val PREFS_NAME = "onboarding"
    private const val KEY_COMPLETED = "completed"

    fun isCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_COMPLETED, false)

    fun setCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }
}
