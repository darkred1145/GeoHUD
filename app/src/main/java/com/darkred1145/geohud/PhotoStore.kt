package com.darkred1145.geohud

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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

        val localTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            .format(Date())
        exif.setAttribute(ExifInterface.TAG_DATETIME, localTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, localTime)
        exif.saveAttributes()
    }

    /**
     * Burns a compact coordinate block into the image pixels. Must run before
     * [tag] because re-encoding drops all EXIF, including GPS.
     */
    fun stamp(file: File, location: Location) {
        val sourceExif = ExifInterface(file.absolutePath)
        val orientation = sourceExif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inMutable = true }
        ) ?: return
        val bitmap = orient(decoded, orientation)
        if (bitmap !== decoded) decoded.recycle()

        try {
            val scale = bitmap.width / 1080f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.MONOSPACE
                textSize = 30f * scale
                setShadowLayer(6f * scale, 0f, 2f * scale, Color.BLACK)
            }
            val lines = buildList {
                add(String.format(Locale.US, "LAT  %.6f", location.latitude))
                add(String.format(Locale.US, "LON  %.6f", location.longitude))
                if (location.hasAltitude()) {
                    add("ALT  ${location.altitude.roundToInt()} m")
                }
                if (location.hasAccuracy()) {
                    add("ACC  \u00B1${location.accuracy.roundToInt()} m")
                }
                add(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(Date())
                )
            }

            val pad = textPaint.textSize * 0.8f
            val lineHeight = textPaint.textSize * 1.35f
            val margin = bitmap.width * 0.035f
            val blockWidth = lines.maxOf { textPaint.measureText(it) } + pad * 2
            val blockHeight = lineHeight * lines.size + pad * 2
            val left = margin
            val bottom = bitmap.height - margin
            val top = bottom - blockHeight

            val canvas = Canvas(bitmap)
            canvas.drawRoundRect(
                RectF(left, top, left + blockWidth, bottom),
                pad, pad,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0) }
            )
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    left + pad,
                    top + pad + lineHeight * (index + 0.8f),
                    textPaint
                )
            }

            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }

        // Re-encoding bakes the rotation in; reset the tag so viewers do not
        // rotate a second time.
        ExifInterface(file.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            saveAttributes()
        }
    }

    private fun orient(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
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
