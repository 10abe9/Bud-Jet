package com.abe.bud_jet.database.preferences


import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager private constructor(context: Context) {
    private val preferences: SharedPreferences

    init {
        preferences = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_NAME = "com.abe.bud_jet.database.preferences.PREFERENCE_MANAGER"
        private const val KEY_IS_FIRST_INIT = "is_first_init"

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
}

