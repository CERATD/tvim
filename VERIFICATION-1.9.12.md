# Verification: Tvim 1.9.12

- Both derived fonts preserve every glyph except zero, plus all advance widths and side bearings; verified with FontTools against the original fonts at their default instance.
- Font preview rendered with Pillow at clock and input sizes and visually inspected: design/square-zero-font-preview.png.
- Font sources and reproduction script: tools/build_square_zero_fonts.py. Derived fonts retain original OFL licenses and use TVIM family names.
- Android runtime visual testing on a phone has not been performed.
- lintDebug and assembleRelease passed. APK signing certificate matches previous releases (SHA-256 5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893).
