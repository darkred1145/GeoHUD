package com.darkred1145.geohud

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import kotlin.math.max

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
        val assets: List<GitHubAsset> = emptyList()
    )

    // --- API ---
    interface GitHubApiService {
        @GET("repos/{owner}/{repo}/releases/latest")
        suspend fun getLatestRelease(
            @retrofit2.http.Path("owner") owner: String,
            @retrofit2.http.Path("repo") repo: String
        ): GitHubRelease
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    private val service: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    // --- LOGIC ---
    suspend fun checkForUpdates(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
        if (nc == null || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No internet connection.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            val remoteVer = latestRelease.tagName.replace("v", "", ignoreCase = true).trim()
            val localVer = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()

            if (isRemoteNewer(remoteVer, localVer)) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(context, latestRelease, localVer)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "System is up to date.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isRemoteNewer(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val length = max(rParts.size, lParts.size)
        for (i in 0 until length) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, localVer: String) {
        AlertDialog.Builder(context)
            .setTitle("New Intel Available")
            .setMessage("Version ${release.tagName} detected.\nCurrent: $localVer")
            .setPositiveButton("Initialize Update") { _, _ ->
                val apkAsset = release.assets.find { it.name.endsWith(".apk", true) }
                if (apkAsset != null) {
                    downloadAndInstall(context, apkAsset.browserDownloadUrl, release.tagName)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                    context.startActivity(intent)
                }
            }
            .setNegativeButton("Abort", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, url: String, version: String) {
        try {
            val fileName = "GeoHUD-$version.apk"
            val request = DownloadManager.Request(url.toUri())
                .setTitle("Downloading GeoHUD Update")
                .setDescription("Version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            Toast.makeText(context, "Download initiated...", Toast.LENGTH_SHORT).show()

            // REGISTER RECEIVER FOR AUTO-INSTALL
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try { ctxt.unregisterReceiver(this) } catch (_: Exception) {}

                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = dm.query(query)
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                val uriString = cursor.getString(uriIndex)
                                if (uriString != null) {
                                    val uri = uriString.toUri()
                                    installApk(ctxt, uri)
                                }
                            }
                        }
                        cursor.close()
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Install Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}