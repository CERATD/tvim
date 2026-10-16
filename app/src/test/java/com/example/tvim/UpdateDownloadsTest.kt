package com.example.tvim

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDownloadManager.ShadowRequest
import org.robolectric.shadows.ShadowSigningInfo
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class UpdateDownloadsTest {
    private lateinit var context: Context
    private lateinit var downloads: UpdateDownloads
    private lateinit var manager: DownloadManager
    private val release = GitHubRelease("v99.0", listOf(GitHubAsset(
        "https://github.com/CERATD/tvim/releases/download/v99.0/Tvim.apk", "Tvim.apk", 3)))
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("update_download", Context.MODE_PRIVATE).edit().clear().commit()
        manager = context.getSystemService(DownloadManager::class.java)
        downloads = UpdateDownloads(context)
    }
    private fun request(): ShadowRequest = Shadow.extract(shadowOf(manager).getRequest(downloads.id()))
    private fun completed(): File {
        val request = request()
        val file = File(requireNotNull(request.destination.path))
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        request.setStatus(DownloadManager.STATUS_SUCCESSFUL)
        return file
    }
    @Test fun downloadSurvivesControllerRecreationAndRepeatedStartDoesNotDuplicateIt() {
        downloads.begin(release)
        request().setStatus(DownloadManager.STATUS_RUNNING)
        request().setBytesSoFar(1)
        request().setTotalSize(3)
        val reloaded = UpdateDownloads(context)
        assertEquals(downloads.id(), reloaded.id())
        assertEquals(release, reloaded.release())
        assertEquals(UpdateProgress(UpdatePhase.DOWNLOADING, 1, 3), reloaded.progress())
        reloaded.begin(release)
        assertEquals(1, shadowOf(manager).requestCount)
        assertEquals("application/vnd.android.package-archive", request().mimeType)
    }
    @Test fun pausedAndFailedDownloadsAreReportedAndCanBeRetried() {
        downloads.begin(release)
        request().setStatus(DownloadManager.STATUS_PAUSED)
        assertEquals(UpdatePhase.WAITING, downloads.progress().phase)
        val oldId = downloads.id()
        request().setStatus(DownloadManager.STATUS_FAILED)
        assertEquals(UpdatePhase.FAILED, downloads.progress().phase)
        downloads.begin(release)
        assertNotEquals(oldId, downloads.id())
        assertEquals(1, shadowOf(manager).requestCount)
    }
    @Test fun completedFileIsReadyAndCancellationClearsState() {
        downloads.begin(release)
        val file = completed()
        assertEquals(UpdatePhase.READY, downloads.progress().phase)
        downloads.cancel()
        assertFalse(file.exists())
        assertEquals(UpdatePhase.IDLE, UpdateDownloads(context).progress().phase)
        assertNull(UpdateDownloads(context).release())
    }
    @Test fun installerUsesReadOnlyContentUri() {
        // AndroidX FileProvider's canonical path containment uses Unix separators.
        // Run this integration assertion on Linux/macOS; it cannot run on Windows Robolectric.
        org.junit.Assume.assumeFalse(File.separatorChar == '\\')
        downloads.begin(release)
        val intent = downloads.installIntent(completed())
        assertEquals("content", intent.data!!.scheme)
        assertEquals(context.packageName + ".updates", intent.data!!.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    @Test fun missingFileCannotBeInstalledEvenWhenDownloadManagerReportsSuccess() {
        downloads.begin(release)
        completed().delete()
        assertEquals(UpdatePhase.FAILED, downloads.progress().phase)
        assertThrows(IllegalStateException::class.java) { downloads.verifiedFile() }
    }
    @Test fun corruptedFileIsRejectedBeforeInstallation() {
        downloads.begin(release.copy(assets = listOf(release.assets.single().copy(digest = "sha256:" + "0".repeat(64)))))
        completed()
        assertThrows(IllegalStateException::class.java) { downloads.verifiedFile() }
    }
    @Test fun installerPermissionIsReadFromAndroid() {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(downloads.canInstall())
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(downloads.canInstall())
        assertEquals("package:" + context.packageName, downloads.permissionIntent().data.toString())
    }
    @Test fun installationChecksActualPackageMetadataAndSigningCertificate() {
        downloads.begin(release)
        val file = completed()
        fun signedPackage(version: Int, name: String, certificate: Byte): PackageInfo = PackageInfo().apply {
            packageName = context.packageName
            versionCode = version
            versionName = name
            signingInfo = ReflectionHelpers.newInstance(SigningInfo::class.java).also {
                Shadow.extract<ShadowSigningInfo>(it).setSignatures(arrayOf(Signature(byteArrayOf(certificate))))
            }
        }
        val pm = shadowOf(context.packageManager)
        pm.installPackage(signedPackage(10, "1.8", 1))
        pm.setPackageArchiveInfo(file.absolutePath, signedPackage(100, "99.0", 1))
        assertEquals(file, downloads.verifiedFile())
        pm.setPackageArchiveInfo(file.absolutePath, signedPackage(100, "99.0", 2))
        assertThrows(IllegalStateException::class.java) { downloads.verifiedFile() }
    }
}
