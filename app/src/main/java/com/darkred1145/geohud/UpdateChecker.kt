package com.darkred1145.geohud

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    // --- DATA MODELS ---
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

    // --- API SERVICE ---
    interface GitHubApiService {
        @GET("repos/{owner}/{repo}/releases/latest")
        suspend fun getLatestRelease(
            @retrofit2.http.Path("owner") owner: String,
            @retrofit2.http.Path("repo") repo: String
        ): GitHubRelease
    }

    // --- RETROFIT INSTANCE ---
    private val json = Json { ignoreUnknownKeys = true }

    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val service: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    // --- MAIN LOGIC ---
    suspend fun checkForUpdates(context: Context) {
        // 0. Check for internet before proceeding
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (networkCapabilities == null || !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No internet connection.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            // 1. Fetch Release Info (Background Thread)
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            // 2. Clean Version Strings (Remove 'v' and whitespace)
            val remoteVersionStr = latestRelease.tagName.replace("v", "", ignoreCase = true).trim()
            val localVersionStr = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()

            // 3. Compare and Show Dialog (Switch to Main Thread for UI)
            if (isRemoteNewer(remoteVersionStr, localVersionStr)) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(context, latestRelease, localVersionStr)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "GeoHUD is up to date!", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Update check failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Compares two version strings (e.g. "1.2.0" vs "1.1.5").
     * Returns true ONLY if remote is strictly greater than local.
     */
    private fun isRemoteNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val length = max(remoteParts.size, localParts.size)

        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }

            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, localVersion: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("New version: ${release.tagName}\nCurrent version: $localVersion")
            .setPositiveButton("Download & Install") { _, _ ->
                // Try to find the APK asset
                val apkAsset = release.assets.find {
                    it.name.endsWith(".apk", ignoreCase = true)
                }

                if (apkAsset != null) {
                    downloadAndInstallApk(context, apkAsset.browserDownloadUrl, release.tagName)
                } else {
                    // Fallback: No APK in assets, just open GitHub page
                    Toast.makeText(context, "APK not found directly. Opening GitHub...", Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                    context.startActivity(intent)
                }
            }
            .setNeutralButton("GitHub") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(context: Context, url: String, version: String) {
        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle("Downloading GeoHUD Update")
                .setDescription("Version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GeoHUD-$version.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                // IMPORTANT: This line makes the notification clickable for installation
                .setMimeType("application/vnd.android.package-archive")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "Downloading... Tap the notification when complete to install.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}