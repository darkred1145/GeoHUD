package com.darkred1145.geohud

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtCurrentPath: TextView
    private lateinit var btnChangeDir: Button
    private lateinit var btnChangeTheme: Button
    private lateinit var btnCheckUpdate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this) // Apply theme first!
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        txtCurrentPath = findViewById(R.id.txtCurrentPath)
        btnChangeDir = findViewById(R.id.btnChangeDir)
        btnChangeTheme = findViewById(R.id.btnChangeTheme)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Set button text to current friendly name
        val currentCode = ThemeHelper.getCurrentTheme(this)
        btnChangeTheme.text = ThemeHelper.getThemeName(currentCode)

        loadPreferences()

        btnBack.setOnClickListener { finish() }

        // Directory Logic
        val directoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                savePathPreference(uri)
                updatePathUI(uri)
            }
        }

        btnChangeDir.setOnClickListener { directoryLauncher.launch(null) }
        btnChangeTheme.setOnClickListener { showThemeSelector() }
        btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, "/// SCANNING FREQUENCIES... NO PACKETS FOUND ///", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePathPreference(uri: Uri) {
        val sharedPref = getSharedPreferences("GeoHUDPrefs", MODE_PRIVATE)
        sharedPref.edit {
            putString("save_directory", uri.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("GeoHUDPrefs", MODE_PRIVATE)
        val savedUriString = sharedPref.getString("save_directory", null)

        if (savedUriString != null) {
            val uri = savedUriString.toUri()
            updatePathUI(uri)
        } else {
            txtCurrentPath.text = "/Pictures/GeoHUD (Default)"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePathUI(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        val name = documentFile?.name ?: uri.path
        txtCurrentPath.text = "EXTERNAL: $name"
    }

    private fun showThemeSelector() {
        val isDynamicSupported = DynamicColors.isDynamicColorAvailable()

        // Define the list of friendly names
        val themeOptions = mutableListOf(
            "Tactical (Red)",
            "Night Vision (Green)",
            "Azure Link (Blue)",
            "Amber Warning (Orange)"
        )

        // Only add Material You if the phone supports it
        if (isDynamicSupported) {
            themeOptions.add("System (Material You)")
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("SELECT INTERFACE SKIN")

        builder.setItems(themeOptions.toTypedArray()) { _, which ->
            val selectedName = themeOptions[which]

            // Map the selected name back to our internal constants
            val selectedCode = when {
                selectedName.contains("Red") -> ThemeHelper.THEME_TACTICAL_RED
                selectedName.contains("Green") -> ThemeHelper.THEME_NIGHT_VISION
                selectedName.contains("Blue") -> ThemeHelper.THEME_AZURE
                selectedName.contains("Orange") -> ThemeHelper.THEME_AMBER
                selectedName.contains("System") -> ThemeHelper.THEME_MATERIAL_YOU
                else -> ThemeHelper.THEME_TACTICAL_RED
            }

            ThemeHelper.saveThemePreference(this, selectedCode)
            Toast.makeText(this, "REBOOTING INTERFACE...", Toast.LENGTH_SHORT).show()

            // Restart App
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
        builder.show()
    }
}