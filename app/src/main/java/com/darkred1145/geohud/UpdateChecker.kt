package com.darkred1145.geohud

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import kotlin.math.max

object UpdateChecker {

    private const val GITHUB_API_URL = "https://api.github.com/"
    private const val REPO_OWNER = "darkred1145"
    private const val REPO_NAME = "GeoHUD"

    @Serializable
    data class GitHubAsset(
        @SerialName("browser_download_url") val browserDownloadUrl: String
    )

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<GitHubAsset> = emptyList()
    )

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

    suspend fun checkForUpdates(context: Context) {
        try {
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            val remoteVersionStr = latestRelease.tagName.replace("v", "", ignoreCase = true).trim()
            val localVersionStr = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()

            if (isRemoteNewer(remoteVersionStr, localVersionStr)) {
                showUpdateDialog(context, latestRelease, localVersionStr)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            .setPositiveButton("Download") { _, _ ->
                val apkAsset = release.assets.find { it.browserDownloadUrl.endsWith(".apk") }
                if (apkAsset != null) {
                    downloadAndInstallApk(context, apkAsset.browserDownloadUrl, release.tagName)
                } else {
                    Toast.makeText(context, "No APK file found for this release.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("View on GitHub") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(context: Context, url: String, version: String) {
        val request = DownloadManager.Request(url.toUri())
            .setTitle("Downloading GeoHUD Update")
            .setDescription("Version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir("Download", "GeoHUD-$version.apk")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(context, "Download started... Check notifications for progress.", Toast.LENGTH_LONG).show()
    }
}