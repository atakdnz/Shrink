# Shrink Project Status

## Current State

Shrink is a buildable Android app scaffold with the core v1 video compression workflow implemented.

Verified command:

```bash
gradle testDebugUnitTest assembleDebug
```

Current result: passing.

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Implemented

- Kotlin Android project with Jetpack Compose.
- Single-screen utility UI with empty, selected, running, result, warning, and error states.
- Manual video picking through Android Photo Picker with SAF fallback.
- Share-sheet handling for single shared videos.
- Metadata loading through `ContentResolver`, `MediaMetadataRetriever`, and `MediaExtractor`.
- Central compression data models and failure states.
- `VideoCompressionEngine` abstraction.
- `Media3CompressionEngine` implementation.
- Central `CompressionPresetMapper` for resolution, codec, FPS, bitrate, target-size, and audio settings.
- H.265 default with explicit H.264 fallback.
- Foreground service compression with progress notification and cancel action.
- Service job request passed through explicit `Intent` extras.
- Output temp/final file management in app-specific Movies storage.
- FileProvider sharing/opening.
- MediaStore saving to Movies/VideoCompressor.
- Storage preflight check.
- H.265 encoder availability check.
- Output larger than input warning.
- Zero-byte output handling.
- Cleaned-up MediaStore pending rows on save failure.
- Unit tests for preset mapping behavior.
- Polished Compose UI with responsive option cards, metadata chips, message panels, result summary, and improved action layout.
- Rotation-aware output sizing for phone portrait videos that are stored as landscape frames with 90/270 degree metadata.
- Improved UI responsiveness by switching the main scroll surface to lazy composition.
- Added an in-app dark mode toggle.
- Replaced the placeholder launcher icon with a cleaner Shrink video-compression mark.

## Known Gaps

- Real device testing has not been performed yet.
- Portrait rotation behavior has a unit-tested sizing fix, but still needs validation with actual device videos.
- HDR handling warns the user, but output color accuracy needs real sample testing.
- iPhone MOV, Samsung HEVC, 4K60, screen recordings, no-audio videos, very short clips, large files, and unusual aspect ratios need device validation.
- Foreground service behavior should be tested with the app backgrounded and the screen off.
- Runtime notification permission is requested, but denied-permission UX can be improved.
- The event bus is process-local; if the process dies during compression, UI state restoration is limited.
- The app has no release signing, CI, or Play distribution setup.

## Excluded From V1

- Batch compression.
- `ACTION_SEND_MULTIPLE`.
- Social-platform presets.
- FFmpeg backend.
- LightCompressor.
- Custom gallery browser.
- Video editor features.
- Analytics, ads, accounts, purchases, cloud upload, or remote config.

## Next Recommended Work

1. Install on a physical Android device and test the full pick, compress, cancel, share, open, and save workflow.
2. Test the required video matrix: portrait, landscape, HDR, iPhone MOV, Samsung 4K60 HEVC, screen recording, no-audio, short clip, large file, 60 FPS, and unusual aspect ratio.
3. Improve state restoration if compression continues while the Activity/process is recreated.
4. Add instrumentation tests for picker/share intents and foreground service behavior where practical.
5. Prepare release signing and CI once device validation is complete.
