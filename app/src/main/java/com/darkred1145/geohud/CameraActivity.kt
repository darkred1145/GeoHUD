package com.darkred1145.geohud

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class CameraActivity : AppCompatActivity(), LocationListener {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var locationManager: LocationManager

    // UI Elements
    private lateinit var viewFinder: PreviewView
    private lateinit var txtLocation: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnCapture: Button
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
        setContentView(R.layout.activity_camera)

        // Keep screen on for tactical usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bind UI
        viewFinder = findViewById(R.id.viewFinder)
        txtLocation = findViewById(R.id.txtLocation)
        txtStatus = findViewById(R.id.txtStatus)
        btnCapture = findViewById(R.id.btnCapture)
        btnBack = findViewById(R.id.btnBack)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Button Logic
        btnCapture.setOnClickListener { takePhoto() }
        btnBack.setOnClickListener { finish() } // Return to Dashboard

        // Permission Check
        if (allPermissionsGranted()) {
            startCamera()
            startLocationUpdates()
        } else {
            requestPermissionsLauncher.launch(requiredPermissions)
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "SENSORS DISABLED: PERMISSION DENIED", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = viewFinder.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Select Back Camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("GeoHUD", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun startLocationUpdates() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            txtStatus.text = "!!! NO SIGNAL SOURCES FOUND !!!"
            txtStatus.setTextColor(Color.RED)
            return
        }

        // 1. Request Updates (Hardware + Network)
        if (isGpsEnabled) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
        }
        if (isNetworkEnabled) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, this)
        }

        // 2. Initial Cache Check (The Compromise)
        // We get the last known location to show SOMETHING immediately,
        // but we will flag it as "Cached" or "Stale" in the UI.
        val lastGps = if (isGpsEnabled) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val lastNet = if (isNetworkEnabled) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

        // Pick the freshest location from cache
        var bestLocation: Location? = null
        if (lastGps != null && lastNet != null) {
            bestLocation = if (lastGps.time > lastNet.time) lastGps else lastNet
        } else if (lastGps != null) {
            bestLocation = lastGps
        } else if (lastNet != null) {
            bestLocation = lastNet
        }

        if (bestLocation != null) {
            // Update UI with the cached location immediately
            updateLocationUI(bestLocation, isCached = true)
        } else {
            txtStatus.text = "/// SCANNING ALL BANDS... ///"
            txtStatus.setTextColor("#D32F2F".toColorInt())
        }
    }

    override fun onLocationChanged(location: Location) {
        // Live updates are never "cached"
        updateLocationUI(location, isCached = false)
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationUI(location: Location, isCached: Boolean) {
        currentLocation = location

        val lat = "%.5f".format(location.latitude)
        val lon = "%.5f".format(location.longitude)

        // 1. Handle Status Text & Color based on Data Quality
        if (isCached) {
            // Calculate how old this data is
            val ageInMillis = System.currentTimeMillis() - location.time
            val isFresh = ageInMillis < 120 * 1000 // Considered "Fresh" if less than 2 minutes old

            if (isFresh) {
                txtStatus.text = "/// TARGET LOCKED (CACHED) ///"
                txtStatus.setTextColor(Color.YELLOW) // Warning: Not live, but usable
            } else {
                txtStatus.text = "/// LAST KNOWN POS (STALE) ///"
                txtStatus.setTextColor(Color.RED) // Error: Data is old, wait for lock
            }
        } else {
            // Live Data
            val source = if (location.provider == LocationManager.GPS_PROVIDER) "SAT" else "NET"
            txtStatus.text = "/// LOCKED ($source) ///"
            txtStatus.setTextColor("#00E676".toColorInt()) // Green: Good to go
        }

        // 2. Update Coordinate Display
        if (location.hasAltitude()) {
            val alt = "${location.altitude.roundToInt()}m"
            txtLocation.text = "LAT: $lat\nLON: $lon\nALT: $alt"
        } else {
            txtLocation.text = "LAT: $lat\nLON: $lon"
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TAG_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoHUD")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("GeoHUD", "Capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri

                    if (savedUri != null) {
                        if (currentLocation != null) {
                            writeGeoTag(savedUri, currentLocation!!)
                            Toast.makeText(baseContext, "IMAGE SECURED + GEO-TAGGED", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(baseContext, "WARNING: NO POSITION DATA", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    private fun writeGeoTag(photoUri: Uri, location: Location) {
        try {
            contentResolver.openFileDescriptor(photoUri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setLatLong(location.latitude, location.longitude)
                if(location.hasAltitude()) {
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, location.altitude.toString())
                }
                exif.saveAttributes()
                Log.d("GeoHUD", "EXIF Data Injected")
            }
        } catch (e: Exception) {
            Log.e("GeoHUD", "EXIF Write Failed", e)
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        cameraExecutor.shutdown()
    }
}