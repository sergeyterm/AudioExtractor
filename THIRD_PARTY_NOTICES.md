# Third-party components

Audio Extractor is distributed under GPL-3.0-or-later. See LICENSE.

- youtubedl-android 0.18.1 (GPL-3.0), library and ffmpeg AAR: https://github.com/yausername/youtubedl-android/tree/0.18.1 .
- yt-dlp (Unlicense, and bundled dependencies under their respective licenses): https://github.com/yt-dlp/yt-dlp .
- FFmpeg (LGPL/GPL depending on build configuration), Android binaries provided by youtubedl-android: https://ffmpeg.org/ and https://github.com/yausername/youtubedl-android/blob/master/BUILD_FFMPEG.md .
- Python and other native runtime components included by youtubedl-android retain their respective licenses. Consult the upstream release and corresponding build instructions for details.

If distributing compiled APKs to third parties, verify the exact native dependencies and provide the corresponding source, build scripts and all required license notices in accordance with their licenses. Upstream source is not vendored.
