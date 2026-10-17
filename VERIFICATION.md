# Verification: Tvim 1.7

- Release build passed.
- 75 tests passed, 0 failures/errors. Robolectric Android API 28 and 33.
- Android Lint: 0 errors.
- APK signature and ZIP/16 KB alignment verified.
- Application ID com.example.tvim; versionCode 9; minimum Android 7.0 (API 24).
- Signing certificate SHA-256 matches previously delivered versions:
  5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893
- APK SHA-256: 4359db337b5c53c3b8ea7457ad420b855b3de662c2a7b251dd71e26f254ecb3d

## New regression coverage

Stopwatch: app recreation, repeated start, pause/resume, reset, cumulative laps, lap limit and recovery after reboot. It runs from elapsed realtime while the app is closed; after device reboot it pauses at the last saved checkpoint.

Sound library: private copy survives original deletion; the same saved sound is reused by multiple alarms; duplicate contents are deduplicated; validation failure does not save an entry or leave temporary files; old alarm URI entries migrate into a shared registry; invalid IDs cannot escape private storage. Playback test verifies the saved private file is actually selected without built-in fallback.

The previous timer, alarm scheduling, playback, vibration and typing tests also pass.

All ten WAV loops were checked for distinct sample hashes, valid 22,050 Hz mono 16-bit PCM, eight-second duration, nonzero RMS, no clipped samples and zero-valued start/end boundaries. These are original synthesized sounds, not recordings.

## Limits

No physical-device listening, vibration or rendered UI test was performed. Old content-URI imports remain dependent on access to their original files; reimporting creates a durable private copy. New imports are limited to 50 MB and validated before they are listed. The same local debug signing key is retained, allowing an update over the supplied 1.6 APK without removing app data.
