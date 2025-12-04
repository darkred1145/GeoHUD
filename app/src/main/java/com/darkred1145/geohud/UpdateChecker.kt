package com.darkred1145.geohud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET

object UpdateChecker {

    private const val GITHUB_API_URL = "https://api.github.com/"
    private const val REPO_OWNER = "darkred1145"
    private const val REPO_NAME = "GeoHUD"

    @Serializable
    data class GitHubRelease(val tag_name: String, val html_url: String)

    interface GitHubApiService {
        @GET("repos/{owner}/{repo}/releases/latest")
        suspend fun getLatestRelease(
            @retrofit2.http.Path("owner") owner: String,
            @retrofit2.http.Path("repo") repo: String
        ): GitHubRelease
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()

    private val service: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    suspend fun checkForUpdates(context: Context) {
        try {
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            val currentVersion = "v" + BuildConfig.VERSION_NAME

            if (latestRelease.tag_name != currentVersion) {
                showUpdateDialog(context, latestRelease)
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., no network
        }
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("A new version (${release.tag_name}) is available. Would you like to update?")
            .setPositiveButton("Update") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.html_url))
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}