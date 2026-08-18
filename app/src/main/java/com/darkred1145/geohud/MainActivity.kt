package com.darkred1145.geohud

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Init Splash Screen
        installSplashScreen()

        // Apply Dynamic Colors (Material You)
        ThemeHelper.applyTheme(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Buttons
        val cameraCard = findViewById<ConstraintLayout>(R.id.cameraCard)
        val galleryCard = findViewById<ConstraintLayout>(R.id.galleryCard)
        val instructionsCard = findViewById<ConstraintLayout>(R.id.instructionsCard)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        // Launch Camera Mode
        cameraCard.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        // Launch Custom Gallery
        galleryCard.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // Show Instructions
        instructionsCard.setOnClickListener {
            showInstructionsDialog()
        }

        // Launch Settings
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showInstructionsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_howto)
            .setMessage(R.string.dialog_body_howto)
            .setPositiveButton(R.string.btn_got_it, null)
            .show()
    }
}