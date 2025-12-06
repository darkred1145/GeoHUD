package com.darkred1145.geohud

import android.app.Activity
import android.content.Context
import com.google.android.material.color.DynamicColors
import androidx.core.content.edit

object ThemeHelper {
    private const val PREF_NAME = "GeoHUDPrefs"
    private const val KEY_THEME = "app_theme"

    // Theme Constants
    const val THEME_TACTICAL_RED = "tactical_red"
    const val THEME_NIGHT_VISION = "night_vision"
    const val THEME_AZURE = "azure_link"
    const val THEME_AMBER = "amber_warning"
    const val THEME_MATERIAL_YOU = "material_you"

    /**
     * Applies the selected theme to the Activity.
     * Must be called BEFORE setContentView() in onCreate().
     */
    fun applyTheme(activity: Activity) {
        val sharedPref = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val theme = sharedPref.getString(KEY_THEME, THEME_TACTICAL_RED)

        // 1. Set the Base XML Style
        // Even for Material You, we need a fallback base style (Red).
        when (theme) {
            THEME_NIGHT_VISION -> activity.setTheme(R.style.Theme_GeoHUD_NightVision)
            THEME_AZURE -> activity.setTheme(R.style.Theme_GeoHUD_Azure)
            THEME_AMBER -> activity.setTheme(R.style.Theme_GeoHUD_Amber)
            else -> activity.setTheme(R.style.Theme_GeoHUD) // Default Red
        }

        // 2. Apply Dynamic Colors (Material You) overlay if selected
        // This overrides the colors set in step 1 with the user's system wallpaper colors.
        if (theme == THEME_MATERIAL_YOU && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /**
     * Saves the user's theme choice to SharedPreferences.
     */
    fun saveThemePreference(context: Context, themeMode: String) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString(KEY_THEME, themeMode)
        }
    }

    /**
     * Retrieves the current saved theme code.
     */
    fun getCurrentTheme(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_THEME, THEME_TACTICAL_RED) ?: THEME_TACTICAL_RED
    }

    /**
     * Returns a human-readable name for the UI button.
     */
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