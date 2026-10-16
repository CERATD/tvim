package com.example.tvim

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

enum class UpdatePhase { IDLE, DOWNLOADING, WAITING, READY, FAILED }
data class UpdateProgress(val phase: UpdatePhase, val bytes: Long = 0, val total: Long = 0)
data class UpdateIdentity(val packageName: String, val version: Long, val name: String, val signers: Set<String>)

object UpdatePolicy {
    fun asset(release: GitHubRelease): GitHubAsset? = release.assets.firstOrNull { asset ->
        runCatching {
            val uri = URI(asset.downloadUrl)
            release.tagName.matches(Regex("v?[0-9]+(\\.[0-9]+)*")) && uri.query == null && uri.fragment == null &&
            asset.name.endsWith(".apk", ignoreCase = true) && uri.scheme == "https" &&
                uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
                uri.path == "/CERATD/tvim/releases/download/${release.tagName}/${asset.name}" &&
                uri.path.substringAfterLast('/') == asset.name &&
                (asset.digest == null || asset.digest.matches(Regex("sha256:[a-fA-F0-9]{64}")))
        }.getOrDefault(false)
    }

    fun accepts(installed: UpdateIdentity, candidate: UpdateIdentity, release: GitHubRelease): Boolean =
        candidate.packageName == installed.packageName && candidate.version > installed.version &&
            candidate.name == release.tagName.removePrefix("v") && installed.signers.isNotEmpty() &&
            candidate.signers == installed.signers
}

/** Android owns the download, so it survives activity/process recreation and network changes. */
class UpdateDownloads(private val context: Context) {
    private val prefs = context.getSharedPreferences("update_download", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(DownloadManager::class.java)
    fun id(): Long = prefs.getLong("id", -1)
    fun release(): GitHubRelease? = runCatching {
        Gson().fromJson(prefs.getString("release", null), GitHubRelease::class.java)
    }.getOrNull()

    private fun file(): File? {
        val name = prefs.getString("file", null) ?: return null
        if (!name.matches(Regex("update-[a-f0-9-]{36}\\.apk"))) return null
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(root, "updates/$name")
    }

    fun progress(): UpdateProgress {
        val saved = release()
        if (saved != null && !VersionOrder.isNewer(saved.tagName, BuildConfig.VERSION_NAME)) {
            cancel(); return UpdateProgress(UpdatePhase.IDLE)
        }
        if (id() < 0) return UpdateProgress(UpdatePhase.IDLE)
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id())).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return UpdateProgress(UpdatePhase.FAILED)
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
                val phase = when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> UpdatePhase.WAITING
                    DownloadManager.STATUS_RUNNING -> UpdatePhase.DOWNLOADING
                    DownloadManager.STATUS_SUCCESSFUL -> if (file()?.isFile == true) UpdatePhase.READY else UpdatePhase.FAILED
                    else -> UpdatePhase.FAILED
                }
                UpdateProgress(phase, bytes, total)
            }
        }.getOrDefault(UpdateProgress(UpdatePhase.FAILED))
    }

    fun begin(release: GitHubRelease) {
        require(VersionOrder.isNewer(release.tagName, BuildConfig.VERSION_NAME))
        val asset = requireNotNull(UpdatePolicy.asset(release))
        if (progress().phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.WAITING, UpdatePhase.READY)) return
        cancel()
        val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        check(File(root, "updates").let { it.isDirectory || it.mkdirs() })
        val name = "update-${UUID.randomUUID()}.apk"
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Tvim ${release.tagName}")
            .setDescription(if (AppSettings(context).language == "ru") "Загрузка обновления" else "Downloading update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$name")
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        val downloadId = manager.enqueue(request)
        if (!prefs.edit().putLong("id", downloadId).putString("release", Gson().toJson(release)).putString("file", name).commit()) {
            manager.remove(downloadId)
            error("Cannot save download")
        }
    }

    fun cancel() {
        val existing = file()
        if (id() >= 0) runCatching { manager.remove(id()) }
        existing?.delete()
        prefs.edit().clear().commit()
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): UpdateIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val hashes = signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        return UpdateIdentity(info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.versionName.orEmpty(), hashes)
    }

    /** Called off the UI thread immediately before every installation attempt. */
    @Suppress("DEPRECATION")
    fun verifiedFile(): File {
        check(progress().phase == UpdatePhase.READY)
        val release = requireNotNull(release())
        val asset = requireNotNull(UpdatePolicy.asset(release))
        val file = requireNotNull(file())
        check(file.length() > 0)
        if (asset.size > 0) check(file.length() == asset.size)
        asset.digest?.let {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            check(digest.digest().joinToString("") { b -> "%02x".format(b) }.equals(it.removePrefix("sha256:"), ignoreCase = true))
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags))
        check(UpdatePolicy.accepts(identity(installed), identity(candidate), release))
        return file
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    fun installIntent(file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", file), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
