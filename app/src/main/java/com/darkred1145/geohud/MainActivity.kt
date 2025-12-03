package com.darkred1145.geohud

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Init Splash Screen
        ThemeHelper.applyTheme(this)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Buttons
        val btnCamera = findViewById<Button>(R.id.btnLaunchCamera)
        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        // Launch Camera Mode
        btnCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        // Launch Custom Gallery (Updated)
        btnGallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // Launch Settings
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}