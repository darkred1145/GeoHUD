package com.darkred1145.geohud

import android.app.Activity
import android.content.Context
import com.google.android.material.color.DynamicColors

object ThemeHelper {
    private const val PREF_NAME = "TerraTagPrefs"
    private const val KEY_THEME = "app_theme"

    // Theme Constants
    const val THEME_TACTICAL_RED = "tactical_red"
    const val THEME_NIGHT_VISION = "night_vision"
    const val THEME_AZURE = "azure_link"
    const val THEME_AMBER = "amber_warning"
    const val THEME_MATERIAL_YOU = "material_you"

    fun applyTheme(activity: Activity) {
        val sharedPref = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val theme = sharedPref.getString(KEY_THEME, THEME_TACTICAL_RED)

        when (theme) {
            THEME_MATERIAL_YOU -> {
                // [FIX] FIRST, set a valid base theme (Red default)
                // This ensures we stop using the non-AppCompat "Splash Theme"
                activity.setTheme(R.style.Theme_TerraTag)

                // THEN, apply the Material You overlay on top of it
                if (DynamicColors.isDynamicColorAvailable()) {
                    DynamicColors.applyToActivityIfAvailable(activity)
                }
            }
            THEME_NIGHT_VISION -> activity.setTheme(R.style.Theme_TerraTag_NightVision)
            THEME_AZURE -> activity.setTheme(R.style.Theme_TerraTag_Azure)
            THEME_AMBER -> activity.setTheme(R.style.Theme_TerraTag_Amber)
            else -> activity.setTheme(R.style.Theme_TerraTag) // Default Red
        }
    }

    fun saveThemePreference(context: Context, themeMode: String) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(KEY_THEME, themeMode)
            apply()
        }
    }

    fun getCurrentTheme(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_THEME, THEME_TACTICAL_RED) ?: THEME_TACTICAL_RED
    }

    fun getThemeName(code: String): String {
        return when(code) {
            THEME_MATERIAL_YOU -> "SYSTEM (MATERIAL YOU)"
            THEME_NIGHT_VISION -> "NIGHT VISION (GREEN)"
            THEME_AZURE -> "AZURE LINK (BLUE)"
            THEME_AMBER -> "AMBER WARNING (ORANGE)"
            else -> "TACTICAL (RED)"
        }
    }
}