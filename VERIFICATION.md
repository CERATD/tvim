# Verification: Tvim 1.8

- Release build passed: testDebugUnitTest lintDebug assembleRelease.
- 93 tests passed, 0 failures/errors; 2 FileProvider integration cases skipped on Windows (95 cases total). Robolectric API 28 and 33.
- Android Lint: 0 errors. Existing warnings are not treated as evidence of a fully warning-free application.
- APK signature and ZIP/16 KB alignment verified.
- Application ID com.example.tvim; versionCode 10; minimum Android 7.0 (API 24).
- Signing certificate SHA-256 matches previously delivered versions:
  5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893
- APK SHA-256: 290dd3e6ccd058f74954d5369e17f655636d52af76108f35e71658c8f63f61ad

## Update regression coverage

Official release URL validation; numeric version ordering; package name, version and certificate checks; DownloadManager request persistence after controller recreation; duplicate start prevention; progress, network wait, failure/retry, missing file, checksum rejection, cancellation and install permission state. Package archive metadata and both matching and mismatched signing certificates are tested through Robolectric PackageManager.

Previous alarm scheduling, playback, vibration, typing, timer, stopwatch and shared sound library tests also pass.

## Limits

No physical-device installation, download across a real process kill/network interruption, listening, vibration or rendered UI test was performed. Download lifetime and network retries are delegated to Android DownloadManager; automated tests simulate its state. AndroidX FileProvider uses Unix canonical path separators, so its two URI integration cases are skipped on this Windows host and remain available for Linux/macOS runners. Final Android installer interaction still needs a device smoke test.

The same local debug signing key is retained, allowing updates over the supplied 1.7 APK without removing app data. Install 1.8 once through its APK link; subsequent releases can use in-app downloads. Android installation permission and user confirmation are required. GitHub SHA-256 is checked when provided; package identity and signature are always checked. Files and download state are excluded from backup.
