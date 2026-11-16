package com.darkred1145.geohud

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File

object UpdateChecker {

    // --- CONFIGURATION ---
    private const val GITHUB_API_URL = "https://api.github.com/"
    private const val REPO_OWNER = "darkred1145"
    private const val REPO_NAME = "GeoHUD"

    // --- MODELS ---
    @Serializable
    data class GitHubAsset(
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        @SerialName("name") val name: String
    )

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("body") val body: String = "",
        val assets: List<GitHubAsset> = emptyList()
    )

    // --- API ---
    interface GitHubApiService {
        @GET("repos/{owner}/{repo}/releases/latest")
        suspend fun getLatestRelease(
            @Path("owner") owner: String,
            @Path("repo") repo: String
        ): GitHubRelease

        @GET("repos/{owner}/{repo}/releases/tags/{tag}")
        suspend fun getReleaseByTag(
            @Path("owner") owner: String,
            @Path("repo") repo: String,
            @Path("tag") tag: String
        ): GitHubRelease
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    private val service: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    // --- LOGIC ---
    /**
     * Fetches the latest release. [silent] suppresses the "up to date" and
     * failure toasts so the startup check does not nag.
     */
    suspend fun checkForUpdates(context: Context, silent: Boolean = false) {
        if (!hasInternet(context)) {
            if (!silent) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.toast_no_internet, Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        try {
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            val localVer = normalize(BuildConfig.VERSION_NAME)

            if (Version.isRemoteNewer(normalize(latestRelease.tagName), localVer)) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(context, latestRelease, localVer)
                }
            } else if (!silent) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.toast_up_to_date, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            if (!silent) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_check_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Shows the changelog once after the app updates. Uses the release body
     * captured by the updater, or fetches the current tag for manual installs.
     * Returns true when a dialog was shown.
     */
    suspend fun maybeShowWhatsNew(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val current = normalize(BuildConfig.VERSION_NAME)
        val lastSeen = prefs.getString(Prefs.KEY_LAST_SEEN_VERSION, null)

        if (lastSeen == current) return false
        if (lastSeen == null) {
            // Fresh install: nothing to announce yet.
            prefs.edit().putString(Prefs.KEY_LAST_SEEN_VERSION, current).apply()
            return false
        }

        val pendingTag = prefs.getString(Prefs.KEY_PENDING_WHATS_NEW_TAG, null)
        val pendingBody = prefs.getString(Prefs.KEY_PENDING_WHATS_NEW_BODY, null)
        val matchesPending = pendingTag != null && normalize(pendingTag) == current

        val body = when {
            matchesPending && !pendingBody.isNullOrBlank() -> pendingBody
            else -> fetchReleaseBody(current) ?: return false // Offline: retry next launch.
        }

        prefs.edit()
            .putString(Prefs.KEY_LAST_SEEN_VERSION, current)
            .remove(Prefs.KEY_PENDING_WHATS_NEW_TAG)
            .remove(Prefs.KEY_PENDING_WHATS_NEW_BODY)
            .apply()

        val changelog = cleanChangelog(body)
        if (changelog.isBlank()) return false

        withContext(Dispatchers.Main) {
            if (activity.isFinishing || activity.isDestroyed) return@withContext
            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.dialog_title_whats_new, current))
                .setMessage(changelog)
                .setPositiveButton(R.string.btn_got_it, null)
                .show()
        }
        return true
    }

    private suspend fun fetchReleaseBody(version: String): String? = try {
        withContext(Dispatchers.IO) {
            service.getReleaseByTag(REPO_OWNER, REPO_NAME, version).body
        }
    } catch (_: Exception) {
        null
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, localVer: String) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        val changelog = cleanChangelog(release.body)
        val message = buildString {
            append(context.getString(R.string.dialog_body_update, release.tagName, localVer))
            if (changelog.isNotBlank()) {
                append("\n\n")
                append(context.getString(R.string.dialog_whats_new_header))
                append("\n")
                append(changelog)
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_title_update)
            .setMessage(message)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                rememberPendingWhatsNew(context, release)
                if (!context.packageManager.canRequestPackageInstalls()) {
                    requestInstallPermission(context)
                } else {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk", true) }
                    if (apkAsset != null) {
                        downloadAndInstall(context, apkAsset.browserDownloadUrl, release.tagName)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                        context.startActivity(intent)
                    }
                }
            }
            .setNegativeButton(R.string.btn_abort, null)
            .show()
    }

    private fun rememberPendingWhatsNew(context: Context, release: GitHubRelease) {
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(Prefs.KEY_PENDING_WHATS_NEW_TAG, release.tagName)
            .putString(Prefs.KEY_PENDING_WHATS_NEW_BODY, release.body)
            .apply()
    }

    private fun requestInstallPermission(context: Context) {
        Toast.makeText(context, R.string.toast_allow_install, Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_install_error, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun downloadAndInstall(context: Context, url: String, version: String) {
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "GeoHUD-$safeVersion-${System.currentTimeMillis()}.apk"

        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.notif_download_title))
            .setDescription(context.getString(R.string.notif_download_desc, version))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setMimeType("application/vnd.android.package-archive")

        val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var progressDialog: AlertDialog? = null
        var waveView: WaveProgressView? = null
        var percentView: TextView? = null

        fun closeProgress() {
            progressDialog?.dismiss()
            progressDialog = null
            pollScope.cancel()
        }

        var expectedId = -1L
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != expectedId) return

                try {
                    appContext.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                closeProgress()

                val status = try {
                    dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            )
                        } else {
                            -1
                        }
                    } ?: -1
                } catch (e: Exception) {
                    -1
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val apkUri = try {
                        val file = File(
                            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                            fileName
                        )
                        if (file.isFile) {
                            FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.provider",
                                file
                            )
                        } else {
                            dm.getUriForDownloadedFile(id)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (apkUri != null) {
                        installApk(ctxt, apkUri)
                    } else {
                        Toast.makeText(ctxt, R.string.toast_download_failed, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(ctxt, R.string.toast_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_download_progress, null)
            waveView = view.findViewById(R.id.waveProgress)
            percentView = view.findViewById(R.id.txtDownloadPercent)
            view.findViewById<TextView>(R.id.txtDownloadVersion).text =
                context.getString(R.string.download_version, version)

            progressDialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_title_download)
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    if (expectedId != -1L) dm.remove(expectedId)
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    closeProgress()
                }
                .create()
                .also { it.show() }
        }

        try {
            // Register before enqueue so a fast download cannot outrun the receiver.
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
            expectedId = dm.enqueue(request)
            Toast.makeText(context, R.string.toast_download_started, Toast.LENGTH_SHORT).show()

            val wave = waveView
            val percent = percentView
            if (wave != null && percent != null) {
                pollScope.launch {
                    while (isActive) {
                        val (downloaded, total) = queryDownloadProgress(dm, expectedId)
                        if (total > 0L) {
                            wave.progress = downloaded.toFloat() / total
                            percent.text = appContext.getString(
                                R.string.download_progress,
                                (downloaded * 100 / total).toInt()
                            )
                        }
                        delay(250L)
                    }
                }
            }
        } catch (e: Exception) {
            closeProgress()
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            Toast.makeText(
                context,
                context.getString(R.string.toast_download_error, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun queryDownloadProgress(dm: DownloadManager, id: Long): Pair<Long, Long> {
        return try {
            dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    downloaded to total
                } else {
                    0L to 0L
                }
            } ?: (0L to 0L)
        } catch (e: Exception) {
            0L to 0L
        }
    }

    private fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_install_error, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun normalize(version: String): String =
        version.replace("v", "", ignoreCase = true).trim()

    /** Release bodies are Markdown; the dialog is plain text, so trim the noise. */
    private fun cleanChangelog(body: String): String = body
        .replace(Regex("(?s)```.*?```"), "")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("(?m)^-\\s+"), "• ")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
