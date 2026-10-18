# Verification: Tvim 1.9.13

- lintDebug and assembleRelease passed.
- TvinMessagesTest covers opt-in defaults, cancellation, persistent edits, replacing schedules, blank-text rejection, stale broadcasts, delivery and duplicate suppression, notification removal, and local time across DST, on Android API 28 and 33.
- Nothing rings now use one circumference-based grid and one draw call per dot; there are no separate active and background grids.
- APK signature verified; certificate SHA-256: 5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893 (same as previous releases).
- Real-device notification delivery under manufacturer battery restrictions and visual testing on a phone have not been performed. Ordinary scheduled notifications may be delayed by Android.
