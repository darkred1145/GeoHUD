package com.darkred1145.geohud

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class CameraActivity : AppCompatActivity(), LocationListener {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var locationManager: LocationManager
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var usingFrontCamera = false
    private var locationUpdatesActive = false

    // UI Elements
    private lateinit var root: View
    private lateinit var dataPanel: View
    private lateinit var bottomBar: View
    private lateinit var viewFinder: PreviewView
    private lateinit var txtLocation: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtMeta: TextView
    private lateinit var statusDot: View
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSwap: ImageButton
    private lateinit var btnBack: ImageButton

    private var currentLocation: Location? = null

    // Permissions
    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION // Added for Network Location
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_camera)
        applyPanelStyle()

        // Keep screen on for tactical usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Restore camera controls after rotation
        if (savedInstanceState != null) {
            flashMode = savedInstanceState.getInt(STATE_FLASH, ImageCapture.FLASH_MODE_OFF)
            usingFrontCamera = savedInstanceState.getBoolean(STATE_FRONT_CAMERA, false)
        }

        // Bind UI
        root = findViewById(R.id.cameraRoot)
        dataPanel = findViewById(R.id.dataPanel)
        bottomBar = findViewById(R.id.bottomBar)
        viewFinder = findViewById(R.id.viewFinder)
        txtLocation = findViewById(R.id.txtLocation)
        txtStatus = findViewById(R.id.txtStatus)
        txtMeta = findViewById(R.id.txtMeta)
        statusDot = findViewById(R.id.statusDot)
        btnCapture = findViewById(R.id.btnCapture)
        btnFlash = findViewById(R.id.btnFlash)
        btnSwap = findViewById(R.id.btnSwap)
        btnBack = findViewById(R.id.btnBack)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Keep HUD panels clear of the status/navigation bars.
        applySystemBarInsets()
        updateFlashIcon()

        // Button Logic
        btnCapture.isEnabled = false
        btnCapture.setOnClickListener { takePhoto() }
        btnBack.setOnClickListener { finish() } // Return to Dashboard
        btnFlash.setOnClickListener { toggleFlash() }
        btnSwap.setOnClickListener {
            usingFrontCamera = !usingFrontCamera
            startCamera()
        }
        txtStatus.setOnClickListener {
            if (!isGpsEnabled() && !isNetworkEnabled()) openLocationSettings()
        }

        // Permission Check
        if (hasCameraPermission() && hasLocationPermission() && hasStoragePermission()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(requiredPermissions)
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!hasCameraPermission()) {
            Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()
            finish()
            return@registerForActivityResult
        }
        startCamera()
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            showNoLocationPermission()
        }
    }

    private fun applySystemBarInsets() {
        val initialTop = (dataPanel.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        val initialBottom = (bottomBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            dataPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = initialTop + bars.top
            }
            bottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialBottom + bars.bottom
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun startCamera() {
        imageCapture = null
        btnCapture.isEnabled = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider failed", e)
                showToast(R.string.toast_camera_failed)
                return@addListener
            }

            val cameraSelector = if (usingFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val hasCamera = try {
                cameraProvider.hasCamera(cameraSelector)
            } catch (e: Exception) {
                Log.e(TAG, "Camera availability check failed", e)
                false
            }

            if (!hasCamera) {
                if (usingFrontCamera) {
                    usingFrontCamera = false
                    showToast(R.string.toast_no_front_camera)
                    startCamera()
                } else {
                    showToast(R.string.toast_camera_failed)
                }
                return@addListener
            }

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = viewFinder.surfaceProvider }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, capture)
                imageCapture = capture
                btnCapture.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                showToast(R.string.toast_camera_failed)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            showNoLocationPermission()
            return
        }

        val gpsEnabled = isGpsEnabled()
        val networkEnabled = isNetworkEnabled()
        val fineLocation = hasFineLocationPermission()

        if ((!fineLocation || !gpsEnabled) && !networkEnabled) {
            showNoSignal()
            return
        }

        locationUpdatesActive = true
        try {
            if (gpsEnabled && fineLocation) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
            }
            if (networkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, this)
            }

            // Show the freshest cached fix immediately while waiting for live data.
            val lastGps = if (gpsEnabled && fineLocation) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            } else {
                null
            }
            val lastNetwork = if (networkEnabled) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            } else {
                null
            }
            val cached = listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }

            if (cached != null) {
                updateLocationUI(cached, isCached = true)
            } else if (currentLocation == null) {
                setStatus(getString(R.string.status_scanning), R.color.hud_text_muted)
                txtMeta.text = ""
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission lost", e)
            locationManager.removeUpdates(this)
            locationUpdatesActive = false
            showNoLocationPermission()
        } catch (e: Exception) {
            Log.e(TAG, "Location updates failed", e)
            locationManager.removeUpdates(this)
            locationUpdatesActive = false
            showNoSignal()
        }
    }

    override fun onLocationChanged(location: Location) {
        // Live updates are never "cached"
        updateLocationUI(location, isCached = false)
    }

    private fun updateLocationUI(location: Location, isCached: Boolean) {
        currentLocation = location

        val lat = String.format(Locale.US, "%.5f", location.latitude)
        val lon = String.format(Locale.US, "%.5f", location.longitude)

        // Status dot encodes fix quality; the label stays neutral so the card reads calmly.
        val meta = mutableListOf<String>()
        if (location.hasAltitude()) {
            meta += getString(R.string.meta_alt, "${location.altitude.roundToInt()}m")
        }
        if (location.hasAccuracy()) {
            meta += getString(R.string.meta_acc, location.accuracy.roundToInt().toString())
        }

        if (isCached) {
            val isFresh = System.currentTimeMillis() - location.time < CACHED_FRESH_MS
            if (isFresh) {
                setStatus(getString(R.string.status_locked_cached), R.color.tactical_amber)
            } else {
                setStatus(getString(R.string.status_last_known), R.color.tactical_red)
            }
        } else {
            val source = if (location.provider == LocationManager.GPS_PROVIDER) {
                getString(R.string.source_gps)
            } else {
                getString(R.string.source_network)
            }
            meta += source
            setStatus(getString(R.string.status_locked, source), R.color.tactical_green)
        }

        txtLocation.text = getString(R.string.coords_2line, lat, lon)
        txtMeta.text = meta.joinToString(" · ")
    }

    private fun setStatus(text: CharSequence, @ColorRes dotColorRes: Int) {
        txtStatus.text = text
        statusDot.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, dotColorRes))
    }

    private fun showNoSignal() {
        locationUpdatesActive = false
        currentLocation = null
        runCatching { locationManager.removeUpdates(this) }
        setStatus(getString(R.string.status_no_signal), R.color.tactical_red)
        txtLocation.text = getString(R.string.coords_placeholder)
        txtMeta.text = ""
    }

    private fun showNoLocationPermission() {
        locationUpdatesActive = false
        currentLocation = null
        runCatching { locationManager.removeUpdates(this) }
        setStatus(getString(R.string.status_no_location_permission), R.color.tactical_red)
        txtLocation.text = getString(R.string.coords_placeholder)
        txtMeta.text = ""
    }

    override fun onProviderEnabled(provider: String) {
        startLocationUpdates()
    }

    override fun onProviderDisabled(provider: String) {
        startLocationUpdates()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun toggleFlash() {
        flashMode = if (flashMode == ImageCapture.FLASH_MODE_ON) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            ImageCapture.FLASH_MODE_ON
        }
        imageCapture?.flashMode = flashMode
        updateFlashIcon()
    }

    private fun updateFlashIcon() {
        btnFlash.setImageResource(
            if (flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val location = currentLocation?.let { Location(it) }
        val name = PhotoStore.newPhotoName(this)
        val temp = try {
            PhotoStore.newTempFile(this)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create temp capture file", e)
            showToast(R.string.toast_capture_failed)
            return
        }

        btnCapture.isEnabled = false
        val outputOptions = ImageCapture.OutputFileOptions.Builder(temp).build()

        try {
            capture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        temp.delete()
                        Log.e(TAG, "Capture failed", exc)
                        runOnUiThread {
                            btnCapture.isEnabled = true
                            showToast(R.string.toast_capture_failed)
                        }
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        var tagged = false
                        if (location != null) {
                            try {
                                PhotoStore.tag(temp, location)
                                tagged = true
                            } catch (e: Exception) {
                                Log.e(TAG, "EXIF write failed", e)
                            }
                        }

                        val savedUri: Uri? = try {
                            PhotoStore.publish(applicationContext, temp, name)
                        } catch (e: Exception) {
                            Log.e(TAG, "Saving capture failed", e)
                            temp.delete()
                            null
                        }

                        runOnUiThread {
                            btnCapture.isEnabled = true
                            when {
                                savedUri == null -> showToast(R.string.toast_capture_failed)
                                tagged -> showToast(R.string.toast_image_secured)
                                location == null -> showToast(R.string.toast_no_position)
                                else -> showToast(R.string.toast_exif_failed)
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            temp.delete()
            btnCapture.isEnabled = true
            Log.e(TAG, "Cannot start capture", e)
            showToast(R.string.toast_capture_failed)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)

    private fun hasFineLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasLocationPermission(): Boolean =
        hasFineLocationPermission() ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasStoragePermission(): Boolean =
        Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private fun isGpsEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun isNetworkEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    private fun openLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open location settings", e)
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(applicationContext, messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission() && hasLocationPermission()) {
            startLocationUpdates()
        } else if (hasCameraPermission()) {
            showNoLocationPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        if (locationUpdatesActive) {
            runCatching { locationManager.removeUpdates(this) }
            locationUpdatesActive = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FLASH, flashMode)
        outState.putBoolean(STATE_FRONT_CAMERA, usingFrontCamera)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { locationManager.removeUpdates(this) }
        cameraExecutor.shutdown()
    }

    private companion object {
        const val TAG = "GeoHUD"
        const val STATE_FLASH = "flash_mode"
        const val STATE_FRONT_CAMERA = "using_front_camera"
        const val CACHED_FRESH_MS = 120 * 1000L
    }
}

