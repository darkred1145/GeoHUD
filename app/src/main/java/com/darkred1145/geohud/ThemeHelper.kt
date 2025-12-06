package com.darkred1145.geohud

import android.app.Activity
import android.content.Context
import com.google.android.material.color.DynamicColors
import androidx.core.content.edit

object ThemeHelper {
    private const val PREF_NAME = "GeoHUDPrefs"
    private const val KEY_THEME = "app_theme"

    // UPDATED THEME CONSTANTS
    const val THEME_MIKU = "theme_miku_01"
    const val THEME_REM = "theme_rem_blue"
    const val THEME_LUKA = "theme_luka_03"
    const val THEME_RIN = "theme_rin_02"
    const val THEME_MATERIAL_YOU = "material_you"

    fun applyTheme(activity: Activity) {
        val sharedPref = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val theme = sharedPref.getString(KEY_THEME, THEME_MIKU)

        // Map constants to the new XML styles
        when (theme) {
            THEME_REM -> activity.setTheme(R.style.Theme_GeoHUD_Rem)
            THEME_LUKA -> activity.setTheme(R.style.Theme_GeoHUD_Luka)
            THEME_RIN -> activity.setTheme(R.style.Theme_GeoHUD_Rin)
            else -> activity.setTheme(R.style.Theme_GeoHUD) // Default Miku
        }

        if (theme == THEME_MATERIAL_YOU && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun saveThemePreference(context: Context, themeMode: String) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString(KEY_THEME, themeMode)
        }
    }

    fun getCurrentTheme(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_THEME, THEME_MIKU) ?: THEME_MIKU
    }

    // UPDATED UI DISPLAY NAMES
    fun getThemeName(code: String): String {
        return when(code) {
            THEME_MATERIAL_YOU -> "SYSTEM (MATERIAL YOU)"
            THEME_REM -> "REM (MAID BLUE)"
            THEME_LUKA -> "LUKA (V4 PINK)"
            THEME_RIN -> "RIN/LEN (VOLTAGE)"
            else -> "MIKU (01 TEAL)"
        }
    }
}