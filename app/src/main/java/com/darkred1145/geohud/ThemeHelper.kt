package com.darkred1145.geohud

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors

object ThemeHelper {
    // UPDATED THEME CONSTANTS
    const val THEME_MIKU = "theme_miku_01"
    const val THEME_REM = "theme_rem_blue"
    const val THEME_LUKA = "theme_luka_03"
    const val THEME_RIN = "theme_rin_02"
    const val THEME_MATERIAL_YOU = "material_you"

    fun applyTheme(activity: Activity) {
        val sharedPref = activity.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val theme = sharedPref.getString(Prefs.KEY_THEME, THEME_MIKU)

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
        val sharedPref = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString(Prefs.KEY_THEME, themeMode)
        }
    }

    fun getCurrentTheme(context: Context): String {
        val sharedPref = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        return sharedPref.getString(Prefs.KEY_THEME, THEME_MIKU) ?: THEME_MIKU
    }

    fun getThemeName(context: Context, code: String): String {
        return context.getString(
            when (code) {
                THEME_MATERIAL_YOU -> R.string.theme_name_material_you
                THEME_REM -> R.string.theme_name_rem
                THEME_LUKA -> R.string.theme_name_luka
                THEME_RIN -> R.string.theme_name_rin
                else -> R.string.theme_name_miku
            }
        )
    }
}
