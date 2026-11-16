package com.darkred1145.geohud

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtCurrentPath: TextView
    private lateinit var txtThemeValue: TextView
    private lateinit var cardDirectory: View
    private lateinit var cardTheme: View
    private lateinit var cardUpdates: View
    private lateinit var txtVersion: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_settings)
        applyPanelStyle()

        val root = findViewById<android.view.View>(R.id.root)
        root.applySystemBarPadding()

        // Bind Views
        txtCurrentPath = findViewById(R.id.txtCurrentPath)
        txtThemeValue = findViewById(R.id.txtThemeValue)
        cardDirectory = findViewById(R.id.cardDirectory)
        cardTheme = findViewById(R.id.cardTheme)
        cardUpdates = findViewById(R.id.cardUpdates)
        txtVersion = findViewById(R.id.txtVersion)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Initialize UI
        txtThemeValue.text = ThemeHelper.getThemeName(this, ThemeHelper.getCurrentTheme(this))

        loadPreferences()
        updateVersionInfo()

        btnBack.setOnClickListener { finish() }

        val txtOpacityValue = findViewById<TextView>(R.id.txtOpacityValue)
        val switchRoundEnds = findViewById<MaterialSwitch>(R.id.switchRoundEnds)
        val prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val savedOpacity = prefs.getInt(Prefs.KEY_PANEL_OPACITY, Prefs.PANEL_OPACITY_DEFAULT)
            .coerceIn(0, 100) / 5 * 5
        txtOpacityValue.text = getString(R.string.opacity_value, savedOpacity)
        switchRoundEnds.isChecked = prefs.getBoolean(Prefs.KEY_ROUND_ENDS, false)

        val slider = findViewById<Slider>(R.id.sliderOpacity)
        slider.value = savedOpacity.toFloat()
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val percent = value.toInt()
            txtOpacityValue.text = getString(R.string.opacity_value, percent)
            prefs.edit { putInt(Prefs.KEY_PANEL_OPACITY, percent) }
            applyPanelStyleTo(findViewById(R.id.root), percent, switchRoundEnds.isChecked)
        }
        switchRoundEnds.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(Prefs.KEY_ROUND_ENDS, checked) }
            applyPanelStyleTo(findViewById(R.id.root), slider.value.toInt(), checked)
        }

        val switchStamp = findViewById<MaterialSwitch>(R.id.switchStamp)
        switchStamp.isChecked = prefs.getBoolean(Prefs.KEY_STAMP_LOCATION, false)
        switchStamp.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(Prefs.KEY_STAMP_LOCATION, checked) }
        }

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

        cardDirectory.setOnClickListener { directoryLauncher.launch(null) }
        cardTheme.setOnClickListener { showThemeSelector() }
        cardUpdates.setOnClickListener {
            cardUpdates.isEnabled = false
            cardUpdates.alpha = 0.5f
            scope.launch {
                try {
                    UpdateChecker.checkForUpdates(this@SettingsActivity)
                } finally {
                    cardUpdates.isEnabled = true
                    cardUpdates.alpha = 1f
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

