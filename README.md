# Shrink

Shrink is a personal Android video compressor app built with Kotlin, Jetpack Compose, and Media3 Transformer.

The app keeps the workflow intentionally small: pick or share one video, choose compression settings, compress it in foreground work, then share, open, or save the result.

## Features

- Pick a video with Android Photo Picker, with `OpenDocument` fallback.
- Receive a single video from Android's share sheet.
- Show video metadata: filename, size, duration, resolution, FPS, codecs, audio presence, and HDR warning when detectable.
- Compress with Media3 Transformer behind a `VideoCompressionEngine` abstraction.
- Default to H.265 / HEVC for smaller output.
- Allow H.264 / AVC as the compatibility fallback.
- Configure quality preset, output resolution, codec, FPS cap, audio removal, target size, and custom bitrate values.
- Run compression through a foreground service with progress notification and cancel support.
- Share/open compressed output through FileProvider content URIs.
- Save compressed output to Movies/VideoCompressor through MediaStore.
- Detect common failure paths such as invalid input, H.265 unavailable, insufficient storage, empty output, failed export, and output larger than input.

## Tech Stack

- Kotlin
- Jetpack Compose
- MVVM with `StateFlow`
- Kotlin Coroutines
- AndroidX Media3 Transformer
- Foreground Service
- Android Photo Picker / Storage Access Framework
- FileProvider
- MediaStore

## Project Structure

```text
app/src/main/java/com/example/shrink
  compression/   Compression models, preset mapping, Media3 engine
  metadata/      Video metadata reader
  service/       Foreground compression service and event bus
  share/         Share intent handling
  storage/       Output files, FileProvider URIs, MediaStore saving
  ui/            Compose screen
  util/          Formatting helpers
  viewmodel/     CompressorViewModel
```

## Build

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Current Limits

- v1 handles one video at a time.
- No trimming, cropping, watermarking, subtitles, social presets, analytics, ads, accounts, or cloud upload.
- Device validation is still needed for real-world video edge cases such as HDR, portrait rotation, iPhone MOV, Samsung HEVC, 4K60, and long background compression.

## License

MIT License. See [LICENSE](LICENSE) for details.
