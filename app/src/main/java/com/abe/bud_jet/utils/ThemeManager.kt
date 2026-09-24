package com.abe.bud_jet.utils

import androidx.appcompat.app.AppCompatDelegate

/** Light, dark or follow the system. Stored as "system", "light" or "dark". */
object ThemeManager {

    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val modes = listOf(SYSTEM, LIGHT, DARK)

    /** Applies the mode; AppCompat recreates visible activities when it changes. */
    fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
