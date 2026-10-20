# Verification: Tvim 1.9.18

- lintDebug and assembleRelease passed.
- 29 targeted tests passed: 17 countdown timer cases (API 28/33), 8 typing occurrence persistence cases (API 28/33), and 4 phrase matching/random selection cases.
- Timer tests cover persisted custom sound selection, per-run sound stability through pause/resume, recreation and subsequent runs.
- 13,440 unique passages, at most 180 characters each, extracted across all paragraphs of the supplied Part I FB2 archive. Extraction script: tools/extract_typing_book.py. English retains the existing translated selection.
- AlarmScreenActivity explicitly uses adjustResize. Typing reference has safe drawing insets and top spacing and is outside the input scroll container.
- Real-device keyboard layout and audio playback have not been tested in this update.
