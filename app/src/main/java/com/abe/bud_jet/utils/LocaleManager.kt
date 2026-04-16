package com.abe.bud_jet.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.abe.bud_jet.R

object LocaleManager {
    fun supportedLanguages(): List<String> = listOf("en", "es", "ru", "pl")

    fun applyAppLanguage(languageCode: String) {
        val safeCode = languageCode.lowercase()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(safeCode))
    }

    fun displayNameForLanguage(context: Context, languageCode: String): String {
        return when (languageCode.lowercase()) {
            "ru" -> context.getString(R.string.language_russian)
            "es" -> context.getString(R.string.language_spanish)
            "pl" -> context.getString(R.string.language_polish)
            else -> context.getString(R.string.language_english)
        }
    }
}
