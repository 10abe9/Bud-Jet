package com.abe.bud_jet.database.preferences


import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class PreferenceManager private constructor(context: Context) {
    private val preferences: SharedPreferences

    init {
        preferences = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_NAME = "com.abe.bud_jet.database.preferences.PREFERENCE_MANAGER"
        private const val KEY_IS_FIRST_INIT = "is_first_init"
        private const val KEY_CURRENCY_CODE = "currency_code"
        private const val KEY_CONVERSION_RATE_PREFIX = "conversion_rate_"
        private const val KEY_APP_LANGUAGE = "app_language"

        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_LAST_DASHBOARD_VISIT = "last_dashboard_visit"
        private const val KEY_LAST_NOTIFICATION_SENT = "last_notification_sent"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_INITIAL_BALANCE = "initial_balance"
        private const val KEY_INITIAL_BALANCE_PROMPT_SHOWN = "initial_balance_prompt_shown"
        private const val KEY_IS_PREMIUM = "is_premium"

        private var instance: PreferenceManager? = null

        @Synchronized
        fun getInstance(context: Context): PreferenceManager {
            if (instance == null) {
                instance = PreferenceManager(context)
            }
            return instance!!
        }
    }

    fun getIsFirstInit(): Boolean {
        return preferences.getBoolean(KEY_IS_FIRST_INIT, true)
    }

    fun setIsFirstInit(state: Boolean) {
        preferences.edit { putBoolean(KEY_IS_FIRST_INIT, state) }
    }

    fun getCurrencyCode(): String {
        return preferences.getString(KEY_CURRENCY_CODE, "USD") ?: "USD"
    }

    fun setCurrencyCode(code: String) {
        preferences.edit { putString(KEY_CURRENCY_CODE, code) }
    }

    fun getAppLanguage(): String {
        val saved = preferences.getString(KEY_APP_LANGUAGE, null)
        if (!saved.isNullOrBlank()) return saved

        val systemLang = LocaleList.getDefault().get(0)?.language?.lowercase().orEmpty()
        return when (systemLang) {
            "ru", "en", "es", "pl" -> systemLang
            else -> "en"
        }
    }

    fun setAppLanguage(code: String) {
        preferences.edit { putString(KEY_APP_LANGUAGE, code.lowercase()) }
    }

    fun observeAppLanguage(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_APP_LANGUAGE) {
                trySend(getAppLanguage())
            }
        }
        trySend(getAppLanguage())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun observeCurrencyCode(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CURRENCY_CODE) {
                trySend(getCurrencyCode())
            }
        }
        trySend(getCurrencyCode())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun getConversionRate(fromCode: String, toCode: String): Double? {
        val key = buildRateKey(fromCode, toCode)
        return if (preferences.contains(key)) preferences.getFloat(key, 1f).toDouble() else null
    }

    fun setConversionRate(fromCode: String, toCode: String, rate: Double) {
        val key = buildRateKey(fromCode, toCode)
        preferences.edit { putFloat(key, rate.toFloat()) }
    }

    fun isNotificationsEnabled(): Boolean {
        return preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled) }
    }

    fun getLastDashboardVisitTime(): Long {
        return preferences.getLong(KEY_LAST_DASHBOARD_VISIT, 0L)
    }

    fun setLastDashboardVisitTime(millis: Long) {
        preferences.edit { putLong(KEY_LAST_DASHBOARD_VISIT, millis) }
    }

    fun getLastNotificationSentTime(): Long {
        return preferences.getLong(KEY_LAST_NOTIFICATION_SENT, 0L)
    }

    fun setLastNotificationSentTime(millis: Long) {
        preferences.edit { putLong(KEY_LAST_NOTIFICATION_SENT, millis) }
    }

    fun isNotificationPermissionRequested(): Boolean {
        return preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    fun setNotificationPermissionRequested(requested: Boolean) {
        preferences.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, requested) }
    }

    fun getInitialBalance(): Double {
        val raw = preferences.getString(KEY_INITIAL_BALANCE, null) ?: return 0.0
        return raw.toDoubleOrNull() ?: 0.0
    }

    fun setInitialBalance(value: Double) {
        preferences.edit { putString(KEY_INITIAL_BALANCE, value.toString()) }
    }

    fun isInitialBalancePromptShown(): Boolean {
        return preferences.getBoolean(KEY_INITIAL_BALANCE_PROMPT_SHOWN, false)
    }

    fun setInitialBalancePromptShown(shown: Boolean) {
        preferences.edit { putBoolean(KEY_INITIAL_BALANCE_PROMPT_SHOWN, shown) }
    }

    fun isPremiumEnabled(): Boolean {
        return preferences.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun setPremiumEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_IS_PREMIUM, enabled) }
    }

    fun resetUserDataToDefaults() {
        val editor = preferences.edit()

        // Remove cached conversion rates.
        val conversionKeys = preferences.all.keys.filter { it.startsWith(KEY_CONVERSION_RATE_PREFIX) }
        conversionKeys.forEach { editor.remove(it) }

        editor.putString(KEY_CURRENCY_CODE, "USD")
        editor.putString(KEY_APP_LANGUAGE, "en")
        editor.putBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        editor.putLong(KEY_LAST_DASHBOARD_VISIT, 0L)
        editor.putLong(KEY_LAST_NOTIFICATION_SENT, 0L)
        editor.putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        editor.remove(KEY_INITIAL_BALANCE)
        editor.putBoolean(KEY_INITIAL_BALANCE_PROMPT_SHOWN, false)
        editor.putBoolean(KEY_IS_PREMIUM, false)
        // Show onboarding again after data deletion.
        editor.putBoolean(KEY_IS_FIRST_INIT, true)

        editor.apply()
    }

    private fun buildRateKey(fromCode: String, toCode: String): String {
        return KEY_CONVERSION_RATE_PREFIX + fromCode.uppercase() + "_" + toCode.uppercase()
    }
}

