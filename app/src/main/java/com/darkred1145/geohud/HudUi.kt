package com.darkred1145.geohud

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Marker for every container that paints a HUD panel. */
const val PANEL_TAG = "hud_panel"

/**
 * HUD is always dark, so force light system-bar icons regardless of the
 * system night mode setting. Target SDK 35 enforces edge-to-edge, therefore
 * every screen must consume system bar insets explicitly.
 */
fun ComponentActivity.enableHudEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
}

/** Adds system bar insets to the view's existing vertical padding. */
fun View.applySystemBarPadding() {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = initialTop + bars.top, bottom = initialBottom + bars.bottom)
        insets
    }
}

/**
 * Applies the user's panel opacity and shape to every tagged panel background.
 * 0 = invisible, 100 = solid; round ends = capsule instead of 16dp corners.
 * Must run after setContentView.
 */
fun Activity.applyPanelStyle() {
    val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    val percent = prefs.getInt(Prefs.KEY_PANEL_OPACITY, Prefs.PANEL_OPACITY_DEFAULT)
    val roundEnds = prefs.getBoolean(Prefs.KEY_ROUND_ENDS, false)
    applyPanelStyleTo(findViewById(android.R.id.content), percent, roundEnds)
}

fun applyPanelStyleTo(view: View, percent: Int, roundEnds: Boolean) {
    val alpha = percent.coerceIn(0, 100) * 255 / 100
    if (view.tag == PANEL_TAG) {
        view.background?.mutate()?.alpha = alpha
        if (view.background is GradientDrawable) {
            val density = view.resources.displayMetrics.density
            view.post {
                val radius = if (roundEnds) {
                    minOf(view.height / 2f, PANEL_CAPSULE_MAX_DP * density)
                } else {
                    PANEL_CORNER_DP * density
                }
                (view.background?.mutate() as? GradientDrawable)?.cornerRadius = radius
            }
        }
    }
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            applyPanelStyleTo(view.getChildAt(index), percent, roundEnds)
        }
    }
}

private const val PANEL_CORNER_DP = 16f

/** Tall panels would become ovals; clamp capsule ends to a sane radius. */
private const val PANEL_CAPSULE_MAX_DP = 44f
