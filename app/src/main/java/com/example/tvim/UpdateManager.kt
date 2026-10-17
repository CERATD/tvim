package com.example.tvim

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)
data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String
)
interface GitHubService {
    @GET("releases/latest") suspend fun getLatestRelease(): GitHubRelease
}
class UpdateManager(private val context: Context) {
    var lastCheckFailed: Boolean = false
        private set
    private val service = Retrofit.Builder().baseUrl("https://api.github.com/repos/CERATD/tvim/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(GitHubService::class.java)

    suspend fun checkForUpdates(): GitHubRelease? = withContext(Dispatchers.IO) {
        lastCheckFailed = false
        try {
            service.getLatestRelease().takeIf { VersionOrder.isNewer(it.tagName, BuildConfig.VERSION_NAME) && it.assets.any { asset -> asset.name.endsWith(".apk") } }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { lastCheckFailed = true; null }
    }
    fun openRelease(release: GitHubRelease) {
        val uri = Uri.parse("https://github.com/CERATD/tvim/releases/tag/" + Uri.encode(release.tagName))
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            android.widget.Toast.makeText(context, uri.toString(), android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
