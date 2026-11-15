package com.darkred1145.geohud

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

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
