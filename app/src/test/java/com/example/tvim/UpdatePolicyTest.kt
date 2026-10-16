package com.example.tvim

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val asset = GitHubAsset("https://github.com/CERATD/tvim/releases/download/v99.0/Tvim.apk", "Tvim.apk")
    private val release = GitHubRelease("v99.0", listOf(asset))
    private val installed = UpdateIdentity("com.example.tvim", 10, "1.8", setOf("certificate"))
    private val candidate = installed.copy(version = 100, name = "99.0")

    @Test fun acceptsOfficialApkAndMatchingNewerSignedPackage() {
        assertEquals(asset, UpdatePolicy.asset(release))
        assertTrue(UpdatePolicy.accepts(installed, candidate, release))
    }
    @Test fun rejectsUntrustedOrAmbiguousDownloadLocations() {
        listOf(
            asset.downloadUrl.replace("https:", "http:"),
            asset.downloadUrl.replace("github.com", "example.com"),
            asset.downloadUrl.replace("CERATD", "someone"),
            asset.downloadUrl.replace("v99.0/", "v98.0/"),
            asset.downloadUrl.replace("Tvim.apk", "nested/Tvim.apk"),
            asset.downloadUrl + "?redirect=1",
            asset.downloadUrl + "#fragment"
        ).forEach { url -> assertNull(url, UpdatePolicy.asset(release.copy(assets = listOf(asset.copy(downloadUrl = url))))) }
        assertNull(UpdatePolicy.asset(release.copy(assets = listOf(asset.copy(name = "other.apk")))))
        assertNull(UpdatePolicy.asset(release.copy(assets = listOf(asset.copy(digest = "invalid")))))
    }
    @Test fun rejectsChangedSignerPackageVersionOrReleaseName() {
        listOf(candidate.copy(signers = emptySet()), candidate.copy(signers = setOf("other")),
            candidate.copy(packageName = "another.app"), candidate.copy(version = 10),
            candidate.copy(version = 9), candidate.copy(name = "98.0")
        ).forEach { assertFalse(UpdatePolicy.accepts(installed, it, release)) }
        assertFalse(UpdatePolicy.accepts(installed.copy(signers = emptySet()), candidate, release))
    }
    @Test fun versionComparisonIsNumericAndRejectsMalformedVersions() {
        assertTrue(VersionOrder.isNewer("v1.10", "1.9"))
        assertFalse(VersionOrder.isNewer("v1.8", "1.8"))
        assertFalse(VersionOrder.isNewer("garbage", "1.8"))
    }
}
