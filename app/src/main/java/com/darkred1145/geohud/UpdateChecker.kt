package com.darkred1145.geohud

import android.content.Context
import android.content.Intent
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
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String
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

            // 1. Sanitize the version strings (remove "v" and extra spaces)
            val remoteVersionStr = latestRelease.tagName.replace("v", "", ignoreCase = true).trim()
            val localVersionStr = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()

            // 2. Compare the versions numerically
            if (isRemoteNewer(remoteVersionStr, localVersionStr)) {
                showUpdateDialog(context, latestRelease, localVersionStr)
            }

        } catch (e: Exception) {
            // Log the error silently or print to stacktrace
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

            if (r > l) return true  // Remote is newer
            if (r < l) return false // Local is newer (Dev build), stop checking
        }

        return false // Versions are identical
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, localVersion: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("New version: ${release.tagName}\nCurrent version: $localVersion\n\nWould you like to download it?")
            .setPositiveButton("Update") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
