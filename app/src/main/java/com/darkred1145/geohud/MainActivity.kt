package com.darkred1145.geohud

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
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
        val btnInstructions = findViewById<Button>(R.id.btnInstructions)

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

        // Show Instructions
        btnInstructions.setOnClickListener {
            showInstructionsDialog()
        }
    }

    private fun showInstructionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to Use GeoHUD")
            .setMessage("1. Grant all requested permissions.\n\n" +
                "2. Use the 'Engage Camera' button to open the camera and capture images.\n\n" +
                "3. Images are automatically tagged with your current GPS coordinates.\n\n" +
                "4. Use the 'View Logs' button to see all your captured images.\n\n" +
                "5. In the settings, you can customize the storage directory and the app's theme.")
            .setPositiveButton("Got it!", null)
            .show()
    }
}