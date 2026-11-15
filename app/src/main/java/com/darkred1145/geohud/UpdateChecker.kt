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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
                Toast.makeText(context, R.string.toast_no_internet, Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val latestRelease = withContext(Dispatchers.IO) {
                service.getLatestRelease(REPO_OWNER, REPO_NAME)
            }

            val remoteVer = latestRelease.tagName.replace("v", "", ignoreCase = true).trim()
            val localVer = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()

            if (Version.isRemoteNewer(remoteVer, localVer)) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(context, latestRelease, localVer)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.toast_up_to_date, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_check_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, localVer: String) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_title_update)
            .setMessage(context.getString(R.string.dialog_body_update, release.tagName, localVer))
            .setPositiveButton(R.string.btn_update) { _, _ ->
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

        var expectedId = -1L
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != expectedId) return

                try {
                    appContext.unregisterReceiver(this)
                } catch (_: Exception) {
                }

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
        } catch (e: Exception) {
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
}
