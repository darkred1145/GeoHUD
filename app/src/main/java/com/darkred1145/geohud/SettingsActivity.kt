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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtCurrentPath: TextView
    private lateinit var btnChangeDir: Button
    private lateinit var btnChangeTheme: Button
    private lateinit var btnCheckUpdate: Button
    private lateinit var txtVersion: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val root = findViewById<android.view.View>(R.id.root)
        root.applySystemBarPadding()

        // Bind Views
        txtCurrentPath = findViewById(R.id.txtCurrentPath)
        btnChangeDir = findViewById(R.id.btnChangeDir)
        btnChangeTheme = findViewById(R.id.btnChangeTheme)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        txtVersion = findViewById(R.id.txtVersion)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Initialize UI
        val currentCode = ThemeHelper.getCurrentTheme(this)
        btnChangeTheme.text = ThemeHelper.getThemeName(this, currentCode)

        loadPreferences()
        updateVersionInfo()

        btnBack.setOnClickListener { finish() }

        // Logic
        val directoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                if (persistTreePermission(uri)) {
                    savePathPreference(uri)
                    updatePathUI(uri)
                } else {
                    Toast.makeText(this, R.string.toast_storage_denied, Toast.LENGTH_LONG).show()
                }
            }
        }

        btnChangeDir.setOnClickListener { directoryLauncher.launch(null) }
        btnChangeTheme.setOnClickListener { showThemeSelector() }
        btnCheckUpdate.setOnClickListener {
            btnCheckUpdate.isEnabled = false
            scope.launch {
                try {
                    UpdateChecker.checkForUpdates(this@SettingsActivity)
                } finally {
                    btnCheckUpdate.isEnabled = true
                }
            }
        }
    }

    /**
     * Providers are allowed to grant read-only access. Persist whatever is
     * available instead of crashing on the write flag.
     */
    private fun persistTreePermission(uri: Uri): Boolean {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = try {
            contentResolver.takePersistableUriPermission(uri, readWrite)
            true
        } catch (e: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (e: SecurityException) {
                false
            }
        }
        if (!persisted) return false

        // A read-only tree cannot receive captures. Reject it up front.
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    private fun savePathPreference(uri: Uri) {
        val sharedPref = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        sharedPref.edit {
            putString(Prefs.KEY_SAVE_DIR, uri.toString())
        }
    }

    private fun loadPreferences() {
        val sharedPref = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val savedUriString = sharedPref.getString(Prefs.KEY_SAVE_DIR, null)
        if (savedUriString != null) {
            updatePathUI(savedUriString.toUri())
        } else {
            txtCurrentPath.text = getString(R.string.default_path)
        }
    }

    private fun updatePathUI(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            val name = documentFile?.name ?: uri.path
            txtCurrentPath.text = getString(R.string.path_external_format, name)
        } catch (e: Exception) {
            txtCurrentPath.text = getString(R.string.default_path)
        }
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

        val themeNames = themeCodes.map { ThemeHelper.getThemeName(this, it) }

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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
