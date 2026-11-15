package com.darkred1145.geohud

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Takes a finished capture from cache and publishes it to the configured
 * destination. EXIF is written before publishing, so the destination never
 * contains an untagged image visible to other apps.
 */
object PhotoStore {
    private const val MIME = "image/jpeg"

    fun newTempFile(context: Context): File =
        File.createTempFile("capture_", ".jpg", context.cacheDir)

    fun newPhotoName(context: Context): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
        return context.getString(R.string.photo_name_format, stamp)
    }

    fun tag(file: File, location: Location) {
        val exif = ExifInterface(file.absolutePath)
        exif.setLatLong(location.latitude, location.longitude)
        if (location.hasAltitude()) exif.setAltitude(location.altitude)
        if (location.hasBearing()) {
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, location.bearing.toString())
        }
        exif.setAttribute(
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            location.provider ?: "unknown"
        )

        // EXIF GPS date/time are UTC, independent of the device time zone.
        val utc = TimeZone.getTimeZone("UTC")
        val date = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply { timeZone = utc }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = utc }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, date.format(Date(location.time)))
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, time.format(Date(location.time)))
        exif.saveAttributes()
    }

    fun publish(context: Context, temp: File, name: String): Uri {
        val savedTree = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .getString(Prefs.KEY_SAVE_DIR, null)
        return try {
            when {
                savedTree != null -> saveToTree(context, savedTree.toUri(), temp, name)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(context, temp, name)
                else -> saveToLegacyPictures(context, temp, name)
            }
        } finally {
            temp.delete()
        }
    }

    private fun saveToTree(context: Context, treeUri: Uri, temp: File, name: String): Uri {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Cannot open target directory")
        tree.findFile(name)?.delete()
        val doc = tree.createFile(MIME, name)
            ?: throw IOException("Cannot create $name")
        try {
            context.contentResolver.openOutputStream(doc.uri, "w")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Cannot write $name")
            return doc.uri
        } catch (e: Exception) {
            doc.delete()
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, temp: File, name: String): Uri {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoHUD")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore rejected $name")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Cannot write $name")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyPictures(context: Context, temp: File, name: String): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GeoHUD"
        )
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.absolutePath}")
        val target = File(dir, name)
        temp.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME), null)
        return Uri.fromFile(target)
    }
}
