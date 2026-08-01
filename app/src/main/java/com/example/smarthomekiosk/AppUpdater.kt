package com.example.smarthomekiosk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/Daddelgreis74/smarthome-kiosk/releases/latest"

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val changelog: String,
        val apkDownloadUrl: String
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.getString("tag_name") // e.g. "v2.1"
                val cleanLatestVersion = tagName.trimStart('v') // "2.1"

                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName ?: "2.1"

                val isAvailable = isVersionNewer(currentVersion, cleanLatestVersion)
                
                var downloadUrl = ""
                if (json.has("assets")) {
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                val changelog = json.optString("body", "")

                return@withContext UpdateInfo(
                    isUpdateAvailable = isAvailable,
                    latestVersion = cleanLatestVersion,
                    changelog = changelog,
                    apkDownloadUrl = downloadUrl
                )
            }
        } catch (e: Exception) {
            Log.e("AppUpdater", "Error checking for updates", e)
        }
        return@withContext UpdateInfo(false, "", "", "")
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val length = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until length) {
                val curr = currentParts.getOrElse(i) { 0 }
                val lat = latestParts.getOrElse(i) { 0 }
                if (lat > curr) return true
                if (curr > lat) return false
            }
        } catch (e: Exception) {
            Log.e("AppUpdater", "Error comparing versions current=$current, latest=$latest", e)
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode == 200) {
                val fileLength = connection.contentLength
                val cacheFile = File(context.cacheDir, "neo-kiosk-update.apk")
                if (cacheFile.exists()) cacheFile.delete()

                connection.inputStream.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                onProgress(total.toFloat() / fileLength.toFloat())
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                return@withContext cacheFile
            }
        } catch (e: Exception) {
            Log.e("AppUpdater", "Error downloading APK", e)
        }
        return@withContext null
    }

    fun startInstallation(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUpdater", "Error starting installation", e)
        }
    }
}
