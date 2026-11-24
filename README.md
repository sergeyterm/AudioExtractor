# Audio Extractor

Native Android application that extracts full audio from eligible HTTPS video sources and saves it as MP3 on the device. Only download content you are allowed to download. No DRM bypass, login or cloud processing.

- Android 10+; ARM64 only; target SDK 36.
- Paste a URL or share a video link to the app; choose 128/192/256/320 kbps.
- Foreground service, progress, cancel, duration check, MediaStore publication to Music/AudioExtractor.
- Source restrictions and changes to video services can prevent some links from working.
- First test: use a short video that you own or have permission to download.

## Build
Java 17, Android SDK 36, Gradle 8.13. Run:
    bash tests/run-rules.sh
    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

GitHub Actions builds a signed debug APK and reports. Download the AudioExtractor-1.0.1-ARM64 artifact from a successful run. Debug signing is temporary unless a persistent release keystore is configured; Android may require uninstalling before replacing a differently signed build.

No personal diagnostic reports, device identifiers or media links are included.

GPL-3.0-or-later; see LICENSE and THIRD_PARTY_NOTICES.md. Verify and provide corresponding sources and notices for bundled dependencies before redistributing APKs.

## 1.0.1 diagnostic update
Separates HTTP 403, HTTP 429, authorization, network, fragment, format and conversion errors; adds local copyable redacted diagnostics and a 90-second network socket timeout. No change to source access restrictions. Long-duration logic tests do not demonstrate live stream availability.
