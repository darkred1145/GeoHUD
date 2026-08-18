package com.darkred1145.geohud

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtCurrentPath: TextView
    private lateinit var btnChangeDir: Button
    private lateinit var btnChangeTheme: Button
    private lateinit var btnCheckUpdate: Button
    private lateinit var txtVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Bind Views
        txtCurrentPath = findViewById(R.id.txtCurrentPath)
        btnChangeDir = findViewById(R.id.btnChangeDir)
        btnChangeTheme = findViewById(R.id.btnChangeTheme)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        txtVersion = findViewById(R.id.txtVersion)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Initialize UI
        val currentCode = ThemeHelper.getCurrentTheme(this)
        btnChangeTheme.text = ThemeHelper.getThemeName(currentCode)

        loadPreferences()
        updateVersionInfo()

        btnBack.setOnClickListener { finish() }

        // Logic
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
            CoroutineScope(Dispatchers.Main).launch {
                UpdateChecker.checkForUpdates(this@SettingsActivity)
            }
        }
    }

    private fun savePathPreference(uri: Uri) {
        val sharedPref = getSharedPreferences("GeoHUDPrefs", MODE_PRIVATE)
        sharedPref.edit {
            putString("save_directory", uri.toString())
        }
    }

    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("GeoHUDPrefs", MODE_PRIVATE)
        val savedUriString = sharedPref.getString("save_directory", null)
        if (savedUriString != null) {
            updatePathUI(savedUriString.toUri())
        } else {
            txtCurrentPath.text = getString(R.string.default_path)
        }
    }

    private fun updatePathUI(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        val name = documentFile?.name ?: uri.path
        txtCurrentPath.text = getString(R.string.path_external_format, name)
    }

    private fun updateVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val longVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            txtVersion.text = getString(R.string.about_format, versionName, longVersionCode)
        } catch (_: PackageManager.NameNotFoundException) {
            txtVersion.text = getString(R.string.about_unknown)
        }
    }

    private fun showThemeSelector() {
        val themeCodes = mutableListOf(
            ThemeHelper.THEME_MIKU,
            ThemeHelper.THEME_REM,
            ThemeHelper.THEME_LUKA,
            ThemeHelper.THEME_RIN
        )

        if (DynamicColors.isDynamicColorAvailable()) {
            themeCodes.add(ThemeHelper.THEME_MATERIAL_YOU)
        }

        val themeNames = themeCodes.map { ThemeHelper.getThemeName(it) }

        val currentCode = ThemeHelper.getCurrentTheme(this)
        val checkedItem = themeCodes.indexOf(currentCode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_theme)
            .setSingleChoiceItems(themeNames.toTypedArray(), checkedItem) { dialog, which ->
                val selectedCode = themeCodes[which]
                ThemeHelper.saveThemePreference(this, selectedCode)
                dialog.dismiss()
                restartApp()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun restartApp() {
        Toast.makeText(this, R.string.toast_rebooting, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}