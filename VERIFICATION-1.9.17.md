# Verification: Tvim 1.9.17

- Regenerated both custom fonts with the four zero corner cells removed; glyph bounds and advance widths are unchanged.
- Compared every other glyph outline and all horizontal metrics against the source fonts using FontTools; unchanged.
- Rendered and visually inspected actual fonts at clock and input sizes: design/square-zero-font-preview.png.
- lintDebug and assembleRelease passed; APK signing certificate matches previous releases (5d8528aa67bed1a67d050b6d607313583ef22cd59cdfdfd3b1fa8040a6f88893).
- On-device visual testing was not performed.
