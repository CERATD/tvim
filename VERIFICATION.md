# Verification: Tvim 1.6

- Release build: passed.
- 53 tests passed, 0 failures/errors. Robolectric Android API 28 and 33.
- Android Lint: 0 errors.
- APK signature and ZIP/16 KB alignment: verified.
- Application ID: com.example.tvim. versionCode: 8. Android minimum: 7.0 (API 24).
- Signing certificate SHA-256 matches the published Tvim 1.5 APK:
  5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893
- APK SHA-256: 30214b6463571e2a59f42fd5cbf6d21502011a2f1eaf60264d27addfebab26d0

## Changes and verification

Restored the analog clock picker. Replaced the Sleep tab with a persistent countdown timer with presets, custom hours/minutes/seconds, pause, resume, reset and alarm-service playback on expiry. Existing wake-up alarms are retained in the Alarm tab.

Timer tests cover duration bounds, duplicate starts, monotonic scheduling, recreation, pause/resume, stale deliveries, reboot restoration, completed timer recovery, denied exact-alarm permission, expiry/dismissal and preservation of existing alarms.

Vibration now explicitly uses alarm attributes on supported Android versions. Tests verify alarm usage, repeating playback, cancellation, finite previews and devices without a vibrator. The vibration picker provides pattern previews and a test button.

All previous alarm, audio and typing tests pass. Existing offline Russian/English typing phrases are retained.

No physical phone or emulator visual/hardware test was performed. Device vibration settings, Do Not Disturb and vendor background restrictions can still affect delivery. No release-signing private key is included; the APK preserves the same local debug certificate as the previous delivered versions.
