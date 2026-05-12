You are building a production-quality personal Android video compressor app.
The app should be simple on the surface but technically robust. It is a single-screen Android utility app that lets the user pick a video or receive a video from Android’s share sheet, compress it, then share/open/save the compressed result.
Do not build unnecessary consumer-app bloat. Do not add social-platform presets like WhatsApp, Instagram, Discord, etc. Do not add batch compression in v1. Do not add benchmarking/debug panels. Do not use LightCompressor. Use Media3 Transformer as the main compression/transcoding backend.
The final result should be clean, maintainable, and realistic for Android.
============================================================
HIGH-LEVEL REQUIREMENTS
============================================================
Build a Kotlin + Jetpack Compose Android app.
Main flows:
1. Manual flow:
   App opens → user taps Pick Video → selects video → app shows metadata → user configures compression → compresses → result shown → user can Share / Open / Save / Compress another.
2. Share-sheet flow:
   User shares a video from Gallery / Files / another app → this app appears as a video share target → app receives the video URI → shows metadata/settings → user compresses → after success, user can immediately share the compressed output. If launched from share sheet, auto-open the Android share sheet after compression by default, or provide a clear “Share compressed video” button.
The UI should be a single Compose screen with state-driven sections:
- Empty state
- Video selected state
- Compression running state
- Result state
- Error state
============================================================
TECH STACK
============================================================
Use:
- Kotlin
- Jetpack Compose
- MVVM
- StateFlow
- Kotlin Coroutines
- Media3 Transformer for compression/transcoding
- Foreground Service for long-running compression
- Android Photo Picker / SAF for selecting input videos
- FileProvider for sharing app-owned output files
- MediaStore for saving final output to user-visible Movies/Gallery
Recommended:
- Min SDK: 26
- Target SDK: 35
Do not use:
- LightCompressor
- FFmpeg in v1
- FFmpegKit in v1
- RxJava
- XML UI
- Broad storage permissions unless absolutely unavoidable
- Custom in-app gallery browser
============================================================
ARCHITECTURE REQUIREMENTS
============================================================
Do not couple the UI or ViewModel directly to Media3 Transformer.
Create a compression abstraction:
interface VideoCompressionEngine {
    suspend fun compress(
        input: CompressionInput,
        settings: CompressionSettings,
        output: CompressionOutput,
        progress: suspend (CompressionProgress) -> Unit
    ): CompressionResult
    fun cancel()
}
Implement the first backend:
class Media3CompressionEngine : VideoCompressionEngine
Design so this can later support:
class FfmpegCompressionEngine : VideoCompressionEngine
But do not implement FFmpeg in v1.
The architecture should roughly be:
- MainActivity
  - Handles incoming intents
  - Hosts Compose UI
  - Connects to ViewModel
- CompressorViewModel
  - Owns CompressorUiState
  - Handles selected video URI
  - Loads metadata
  - Updates settings
  - Starts compression through service/repository
  - Observes compression progress/result
  - Exposes user actions to UI
- VideoMetadataReader
  - Reads filename, file size, duration, resolution, FPS if available, codec if available, audio presence, HDR if detectable, rotation if available
- CompressionRepository
  - Coordinates compression requests
  - Talks to foreground service or service controller
  - Provides progress/result as Flow
- VideoCompressionEngine
  - Abstract interface
- Media3CompressionEngine
  - Actual Media3 implementation
- OutputFileManager
  - Creates temp files
  - Creates final output files
  - Deletes temp files on failure/cancel
  - Generates FileProvider URIs
  - Saves final file to MediaStore
- ShareIntentHandler
  - Extracts incoming video URI from ACTION_SEND
  - Handles MIME type validation
- CompressionPresetMapper
  - Converts UI settings + input metadata into concrete encoding config
============================================================
DATA MODELS
============================================================
Create clear models.
VideoInfo:
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val fps: Float?,
    val videoCodec: String?,
    val audioCodec: String?,
    val hasAudio: Boolean,
    val isHdr: Boolean?,
    val rotationDegrees: Int?
)
CompressionSettings:
data class CompressionSettings(
    val preset: CompressionPreset,
    val resolution: OutputResolution,
    val codec: OutputCodec,
    val fpsMode: FpsMode,
    val audioMode: AudioMode,
    val targetSizeBytes: Long?,
    val customVideoBitrate: Int?,
    val customAudioBitrate: Int?
)
Enums:
enum class CompressionPreset {
    HIGH,
    BALANCED,
    SMALL,
    TINY,
    CUSTOM
}
enum class OutputCodec {
    H265_HEVC,
    H264_AVC
}
enum class OutputResolution {
    ORIGINAL,
    P1080,
    P720,
    P480,
    P360
}
enum class FpsMode {
    ORIGINAL,
    FPS_60,
    FPS_30,
    FPS_24
}
enum class AudioMode {
    KEEP,
    REMOVE
}
CompressionInput:
data class CompressionInput(
    val uri: Uri,
    val videoInfo: VideoInfo
)
CompressionOutput:
data class CompressionOutput(
    val tempFile: File,
    val finalFile: File
)
CompressionProgress:
data class CompressionProgress(
    val percent: Float,
    val message: String? = null
)
CompressionResult:
sealed interface CompressionResult {
    data class Success(
        val outputFile: File,
        val outputSizeBytes: Long,
        val originalSizeBytes: Long?,
        val outputUri: Uri?
    ) : CompressionResult
    data class Failure(
        val reason: CompressionFailureReason,
        val message: String,
        val throwable: Throwable? = null
    ) : CompressionResult
    data object Cancelled : CompressionResult
}
CompressionFailureReason:
enum class CompressionFailureReason {
    INPUT_URI_INVALID,
    INPUT_METADATA_FAILED,
    UNSUPPORTED_INPUT,
    DECODER_FAILED,
    ENCODER_FAILED,
    H265_UNAVAILABLE,
    STORAGE_INSUFFICIENT,
    OUTPUT_CREATION_FAILED,
    MEDIA3_EXPORT_FAILED,
    CANCELLED,
    OUTPUT_LARGER_THAN_INPUT,
    UNKNOWN
}
UI state:
data class CompressorUiState(
    val selectedVideo: VideoInfo? = null,
    val settings: CompressionSettings = CompressionSettings.default(),
    val jobState: CompressionJobState = CompressionJobState.Idle,
    val output: CompressedVideo? = null,
    val launchedFromShareSheet: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)
Job state:
sealed interface CompressionJobState {
    data object Idle : CompressionJobState
    data object LoadingMetadata : CompressionJobState
    data object Preparing : CompressionJobState
    data class Running(val progress: Float) : CompressionJobState
    data object Cancelling : CompressionJobState
    data class Success(val result: CompressionResult.Success) : CompressionJobState
    data class Failed(val reason: CompressionFailureReason, val message: String) : CompressionJobState
    data object Cancelled : CompressionJobState
}
CompressedVideo:
data class CompressedVideo(
    val file: File,
    val contentUri: Uri,
    val originalSizeBytes: Long?,
    val outputSizeBytes: Long,
    val compressionRatioPercent: Float?
)
============================================================
DEFAULT COMPRESSION SETTINGS
============================================================
Default settings must be:
Preset:
- Balanced
Codec:
- H.265 / HEVC by default
Resolution:
- If input height or width is above 1080p-equivalent, default output max should be 1080p.
- If input is 1080p or below, keep original resolution.
FPS:
- If input FPS is above 30, cap to 30 FPS.
- Otherwise keep original.
Audio:
- Keep audio.
Rationale:
The goal is smaller file size, not maximum compatibility, so H.265 should be default. H.264 must still be selectable as the compatibility option.
============================================================
UI REQUIREMENTS
============================================================
Single screen only.
Use Jetpack Compose.
The UI should be simple, clean, and practical.
------------------------------------------------------------
EMPTY STATE
------------------------------------------------------------
Show:
Title:
Video Compressor
Description:
Pick a video to compress, or share a video from Gallery/Files to this app.
Primary button:
Pick Video
No unnecessary onboarding.
------------------------------------------------------------
VIDEO SELECTED STATE
------------------------------------------------------------
Show a card with metadata:
- Filename
- Original size
- Duration
- Resolution
- FPS if known
- Video codec if known
- Audio presence
- HDR warning if detected
- Rotation/orientation does not need to be shown unless useful
Example:
filename.mp4
742 MB
3840 × 2160
01:23
HEVC
60 FPS
HDR detected
If HDR is detected, show a warning:
HDR video detected. Compressed output may lose HDR/color accuracy.
------------------------------------------------------------
SETTINGS UI
------------------------------------------------------------
Show main settings directly:
Quality:
- High
- Balanced
- Small
- Tiny
Resolution:
- Original
- 1080p
- 720p
- 480p
- 360p
Codec:
- H.265 — smaller size
- H.264 — better compatibility
Advanced section:
Collapsed by default.
Advanced options:
- Target file size
- Custom video bitrate
- Custom audio bitrate
- FPS cap: Original / 60 / 30 / 24
- Remove audio
Do not include:
- WhatsApp preset
- Instagram preset
- Discord preset
- TikTok preset
- Email preset
- Any platform-specific preset
------------------------------------------------------------
ACTION BUTTONS
------------------------------------------------------------
When video is selected and not compressing:
Primary:
Compress
Secondary:
Clear
When compression is running:
Show:
- Progress bar
- Percent text
- Cancel button
When compression succeeds:
Show:
- Original size → output size
- Percent smaller
- Share button
- Open button
- Save to Movies/Gallery button
- Compress another button
If launched from Android share sheet:
- After successful compression, automatically open Android share sheet with the compressed file by default.
- Also keep result UI available in case auto-share fails or user comes back.
============================================================
QUALITY PRESET MAPPING
============================================================
Centralize all preset logic in:
object CompressionPresetMapper
Do not scatter bitrate/resolution/FPS logic in UI, ViewModel, and engine.
Create function:
fun mapToEncodingConfig(
    videoInfo: VideoInfo,
    settings: CompressionSettings
): EncodingConfig
EncodingConfig should include:
data class EncodingConfig(
    val outputWidth: Int?,
    val outputHeight: Int?,
    val videoMimeType: String,
    val videoBitrate: Int?,
    val audioBitrate: Int?,
    val fps: Int?,
    val removeAudio: Boolean
)
Use sensible starting values, but keep them easy to adjust.
Preset behavior:
HIGH:
- Prefer preserving original quality.
- Keep original resolution unless input is extremely high resolution.
- Keep FPS unless very high.
- Use higher bitrate.
- Use selected codec, default H.265.
BALANCED:
- Default preset.
- Max 1080p.
- Max 30 FPS.
- Medium bitrate.
- Use selected codec, default H.265.
SMALL:
- Max 720p.
- Max 30 FPS.
- Lower bitrate.
- Use selected codec, default H.265.
TINY:
- Max 480p.
- Max 24 or 30 FPS.
- Aggressive bitrate.
- Use selected codec, default H.265.
CUSTOM:
- Use explicitly selected resolution, FPS, bitrate, audio options, and target size logic.
Suggested initial bitrate ranges:
For H.265:
- 1080p High: 5–8 Mbps
- 1080p Balanced: 3–5 Mbps
- 720p Balanced/Small: 1.5–3 Mbps
- 480p Tiny: 0.7–1.5 Mbps
For H.264:
- Use somewhat higher bitrate than H.265 for similar quality.
- Example: H.264 bitrate can be roughly 1.3x to 1.6x the H.265 value.
Do not treat these as mathematically perfect. Keep values centralized so they can be tuned.
============================================================
TARGET FILE SIZE MODE
============================================================
Advanced target-size mode should be approximate.
If targetSizeBytes is set:
durationSeconds = durationMs / 1000.0
targetTotalBitrateBitsPerSecond =
    targetSizeBytes * 8 / durationSeconds
If audio is kept:
videoBitrate =
    targetTotalBitrateBitsPerSecond - audioBitrate
If audio is removed:
videoBitrate =
    targetTotalBitrateBitsPerSecond
Clamp video bitrate to safe minimums.
If calculated bitrate is too low, show warning:
Target size may be too small for this video. Output quality may be poor.
Always label target-size estimates as approximate.
Do not promise exact output size.
============================================================
MEDIA3 TRANSFORMER REQUIREMENTS
============================================================
Use Media3 Transformer as the primary backend.
The Media3CompressionEngine should:
- Accept content Uri input.
- Build a MediaItem from the Uri.
- Apply requested transformation settings.
- Configure output codec:
  - H.265 / HEVC when OutputCodec.H265_HEVC
  - H.264 / AVC when OutputCodec.H264_AVC
- Apply resolution scaling when requested.
- Apply FPS cap if Media3/device support allows.
- Remove audio when requested.
- Export to temp output file.
- Report progress to caller.
- Return CompressionResult.Success on successful export.
- Return structured CompressionResult.Failure on failure.
- Support cancellation.
Important:
If Media3 does not support one requested knob directly on the current version/device, fail gracefully or ignore only with an explicit warning in code/UI. Do not silently pretend everything worked.
H.265 handling:
- H.265 is default.
- If H.265 encoder is unavailable or export fails due to encoder initialization, return H265_UNAVAILABLE or ENCODER_FAILED.
- UI should offer retry with H.264.
- Do not silently switch from H.265 to H.264 unless the user explicitly chooses retry.
============================================================
FOREGROUND SERVICE REQUIREMENTS
============================================================
Compression must run in a foreground service, not only in the Activity or ViewModel lifecycle.
Reason:
Video compression can take minutes. The screen may turn off. The app may go to background. The user must be able to cancel.
Implement:
CompressionForegroundService
Responsibilities:
- Receive compression job request.
- Run compression through CompressionRepository/VideoCompressionEngine.
- Show notification while compressing.
- Update notification progress.
- Provide cancel action.
- Clean up temp file on cancel/failure.
- Emit progress/result to app state.
Notification:
Title:
Compressing video
Content:
42%
Action:
Cancel
When done:
Either remove notification or show completion notification depending on UX.
Android foreground service type:
Use the correct foreground service declaration for target SDK 35. Declare required foreground service permissions/types for media processing as needed.
============================================================
INPUT HANDLING
============================================================
Support in-app picking:
Prefer:
ActivityResultContracts.PickVisualMedia
Use video-only picker.
Fallback if needed:
ActivityResultContracts.OpenDocument
or
ActivityResultContracts.GetContent
Do not request READ_MEDIA_VIDEO unless you later build a custom gallery browser.
Support share sheet:
Manifest intent filter on MainActivity:
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="video/*" />
</intent-filter>
Handle:
Intent.ACTION_SEND
Read:
Intent.EXTRA_STREAM
Validate:
- URI exists
- MIME type starts with video/
- ContentResolver can open it
Do not assume the URI is a file path.
Do not use real-path hacks.
Use ContentResolver.
ACTION_SEND_MULTIPLE is excluded from v1.
============================================================
METADATA READING
============================================================
Create VideoMetadataReader.
It should read:
- display name
- size
- duration
- width
- height
- FPS if available
- video codec if available
- audio codec if available
- has audio
- HDR if detectable
- rotation degrees if available
Use ContentResolver, MediaMetadataRetriever, and/or Media3 metadata APIs where appropriate.
Handle failure gracefully:
If some metadata cannot be read, still allow compression if URI is valid.
Example:
If FPS is unknown, show “FPS unknown” or omit it.
If codec is unknown, show “Codec unknown” or omit it.
Do not crash because one metadata field is missing.
============================================================
STORAGE REQUIREMENTS
============================================================
Use app-specific external Movies directory for working files:
context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
Create structure:
Movies/
  VideoCompressor/
    temp/
    output/
Output process:
1. Create temp output file:
   temp/{jobId}.mp4
2. Compress into temp file.
3. On success:
   Move/rename to final output:
   output/{originalName}_compressed_{timestamp}.mp4
4. On failure:
   Delete temp file.
5. On cancellation:
   Delete temp file.
6. Never expose file:// URIs.
Use FileProvider for sharing app-owned output.
============================================================
FILEPROVIDER REQUIREMENTS
============================================================
Configure FileProvider.
Manifest:
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
res/xml/file_paths.xml should allow sharing files from the app-specific Movies output directory.
When sharing:
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "video/mp4"
    putExtra(Intent.EXTRA_STREAM, outputContentUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
Use Intent.createChooser.
============================================================
SAVE TO MOVIES / GALLERY
============================================================
Implement “Save to Movies” using MediaStore.
Do not write directly to arbitrary public file paths.
Process:
- Create MediaStore video item.
- Set display name.
- Set MIME type video/mp4.
- Set relative path Movies/VideoCompressor if supported.
- Open output stream.
- Copy compressed output file to MediaStore output stream.
- Mark item complete if using IS_PENDING.
After save:
Show success message.
============================================================
OPEN OUTPUT
============================================================
Open compressed video with ACTION_VIEW.
Use FileProvider content URI.
Intent:
- ACTION_VIEW
- type video/mp4
- FLAG_GRANT_READ_URI_PERMISSION
Wrap in try/catch.
If no app can open it, show a user-friendly message.
============================================================
SHARE OUTPUT
============================================================
Share compressed output with ACTION_SEND.
Use FileProvider content URI.
Use MIME type video/mp4.
Grant read permission.
If launched from share sheet:
After success, automatically show share chooser unless disabled or already shown.
============================================================
ERROR HANDLING
============================================================
Handle these explicitly:
- Input URI invalid
- Input cannot be opened
- MIME type is not video/*
- Metadata read failed
- Unsupported input format
- Decoder initialization failed
- Encoder initialization failed
- H.265 unavailable
- H.265 export failed
- Not enough storage
- Output temp file cannot be created
- Media3 export failed
- Compression cancelled by user
- Output file missing after export
- Output file is 0 bytes
- Output larger than input
- Save to MediaStore failed
- Share intent failed
- Open intent failed
- Foreground service start failure
UI error examples:
H.265 unavailable:
“H.265 encoding is not available on this device. Retry with H.264?”
Output larger than original:
“Compressed output is larger than the original. You may want to keep the original or retry with a smaller preset.”
Invalid input:
“This video could not be opened. Try selecting it from Files instead.”
Not enough storage:
“Not enough storage space to compress this video.”
============================================================
HDR / ROTATION / DEVICE WEIRDNESS
============================================================
Detect HDR if practical.
If HDR detected:
Show warning:
HDR video detected. Compressed output may lose HDR/color accuracy.
Do not block compression.
Preserve orientation/rotation correctly.
Test portrait videos carefully.
The output must not become sideways.
Test with:
- portrait video
- landscape video
- Samsung 4K60 HEVC
- iPhone MOV
- screen recording
- HDR video
- video without audio
- short video under 5 seconds
- large video over 1GB
- 60 FPS source
- weird aspect ratio source
============================================================
PERMISSIONS
============================================================
Avoid broad storage permissions.
Do not request:
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- MANAGE_EXTERNAL_STORAGE
Do not request READ_MEDIA_VIDEO unless absolutely necessary.
Expected permissions:
- FOREGROUND_SERVICE
- correct foreground service type permission for media processing if required by target SDK
Use picker/share URIs for input access.
Use app-specific storage for temporary/final app-owned files.
Use MediaStore for saving visible output.
============================================================
CANCELLATION
============================================================
User must be able to cancel compression.
Cancel button in UI:
- sends cancel request to foreground service/engine
- updates UI state to Cancelling
- engine cancels Media3 export
- temp output file is deleted
- notification is removed/updated
- UI state becomes Cancelled
Do not leave corrupted partial output files.
============================================================
PROGRESS
============================================================
Show progress in UI and notification.
Progress model:
- Float from 0f to 1f
- Render as percent
If exact progress is not available at some stage:
Show indeterminate progress during preparation.
Switch to determinate when export progress is available.
States:
- Loading metadata
- Preparing compression
- Compressing x%
- Cancelling
- Complete
- Failed
============================================================
OUTPUT SIZE AND RATIO
============================================================
After success, calculate:
originalSizeBytes
outputSizeBytes
Percent smaller:
percentSmaller =
    (1 - outputSizeBytes / originalSizeBytes) * 100
Handle null original size:
If original size is unknown, show only output size.
Handle output larger than input:
Show warning.
Do not call it “compressed smaller.”
============================================================
CODE QUALITY REQUIREMENTS
============================================================
Keep code modular.
Do not put everything in MainActivity.
Avoid god classes.
Use meaningful package structure, for example:
com.example.videocompressor
  ui/
    CompressorScreen.kt
    components/
  viewmodel/
    CompressorViewModel.kt
  compression/
    VideoCompressionEngine.kt
    Media3CompressionEngine.kt
    CompressionPresetMapper.kt
    models.kt
  metadata/
    VideoMetadataReader.kt
  storage/
    OutputFileManager.kt
    MediaStoreSaver.kt
    FileProviderHelper.kt
  service/
    CompressionForegroundService.kt
  share/
    ShareIntentHandler.kt
  util/
    Formatters.kt
Use formatter utilities:
- formatBytes
- formatDuration
- formatResolution
- formatPercent
Use sealed classes for structured states/errors.
============================================================
DEPENDENCIES
============================================================
Use current stable AndroidX/Media3 dependencies.
Likely dependencies:
- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.lifecycle:lifecycle-viewmodel-compose
- androidx.activity:activity-compose
- androidx.compose BOM
- androidx.media3:media3-transformer
- androidx.media3:media3-common
- androidx.media3:media3-effect if needed
- Kotlin coroutines Android
Do not add unnecessary libraries.
============================================================
MANIFEST REQUIREMENTS
============================================================
MainActivity:
- exported true if it has share intent filter
- ACTION_SEND video/* intent filter
FileProvider:
- configured correctly
- non-exported
- grantUriPermissions true
Foreground service:
- declared
- correct foreground service type for media processing
- required permissions for target SDK 35
============================================================
EXCLUDED FROM V1
============================================================
Do not implement:
- Batch compression
- ACTION_SEND_MULTIPLE
- WhatsApp/Instagram/Discord/TikTok/email presets
- Full video editor
- Trimming UI
- Cropping UI
- Watermarking
- Subtitles
- Cloud upload
- Benchmarking/debug panel
- LightCompressor
- FFmpeg backend
- Custom gallery browser
- Account/login
- Analytics
- Ads
- Remote config
- In-app purchases
============================================================
ACCEPTANCE CRITERIA
============================================================
The app is acceptable when:
1. It builds successfully.
2. User can pick a video inside the app.
3. User can share a video from Gallery/Files to the app.
4. App shows basic video metadata.
5. Default compression uses H.265/HEVC.
6. User can switch to H.264/AVC.
7. User can choose quality preset.
8. User can choose output resolution.
9. Compression runs as foreground user-visible work.
10. Progress is visible in UI and notification.
11. User can cancel compression.
12. Cancelled compression deletes temp output.
13. Successful compression creates a playable MP4.
14. Result screen shows original size, output size, and percent smaller when original size is known.
15. User can share compressed output.
16. User can open compressed output.
17. User can save compressed output to Movies/Gallery via MediaStore.
18. FileProvider sharing works without file:// URI exposure.
19. H.265 unavailable/failure case is handled and offers retry with H.264.
20. Output-larger-than-input case is detected and shown clearly.
21. App does not request unnecessary broad storage permissions.
22. App does not crash when metadata fields are missing.
23. App handles invalid/non-video shared content gracefully.
24. Portrait videos do not become sideways.
25. Temp files are cleaned after failure/cancel.
============================================================
FINAL IMPORTANT NOTES
============================================================
Prioritize correctness and Android compatibility over fancy UI.
The UI should stay minimal and practical.
The app is for personal use, but it should still be clean enough to survive real Android device weirdness.
Default to H.265 because the goal is smaller output.
Keep H.264 as the manual compatibility fallback.
Do not silently change user-selected codec.
Keep preset and encoding logic centralized.
Use Media3 first.
Keep the compression engine abstraction clean so another backend can be added later.
