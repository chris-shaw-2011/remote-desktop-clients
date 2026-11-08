# Quest 3 dependency baseline

Captured November 8, 2026 from `75f9a2af5291b31daff831fa073fe912089d5835`. The update plan was an untracked document at capture. This is a local build baseline, not evidence of the signed APK installed on a headset. The extracted native tree and binaries are ignored, pre-existing outputs; their provenance and exact compiled flags are not independently established by the Gradle build.

## Native inputs and toolchain

`remoteClientLib/jni/libs/build-deps.conf` pins FreeRDP 2.11.7, OpenSSL 1.1.1w (SHA-256 `cf3098950cb4d853ad95c0841f1f9c6d3dc102dccfcacd521d93925208b76ac8`), OpenH264 v2.5.0 (disabled), FreeRDP NDK r25c and CMake 3.23.0; the general NDK is r28c and Cerbero NDK is r25c. The build script sets release, JPEG on, FFmpeg on, OpenH264 off and native Android API 21. The existing extracted `android-build.conf` specifies ARM64, MediaCodec on, OpenSSL on, FFmpeg n4.4.1 at commit `82b43cc67296bcd01a59ae6b327cdb50121d3a9e35f41a30de1edd71bb4a6666`, and OpenSSL 1.1.1w. These settings describe configuration, not independently verified compiled flags for the reused binaries. The FreeRDP patch adds a 16 KiB maximum linker page size; actual packaged ELF alignment still needs inspection.

The local Gradle wrapper is 9.6.0, Android Gradle Plugin 9.4.1, and JDK OpenJDK 21.0.12.1; shared minimum SDK is 21. The local release build reuses native outputs and does not rebuild the native stack. The workflow's personal release is ARM64; local Gradle configuration also generates other splits.

SHA-256 of the existing ARM64 FreeRDP input binaries, under `remoteClientLib/jni/libs/deps/FreeRDP/client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a/`:

```text
a9238e7650d74cb31eb2f981bd8a0ce15fd9b46b5877dfd6a4b24f967c132a53  libavcodec.so
f479724063c9186684dd5637b3f27aa22cadcd424cb8b3d425c96e1568f19f9d  libavutil.so
27e8200be5a1aa1c6567c14104c860464c0c5015b6b2f4dbf26035d0c1129236  libcrypto.so
61f7903ded0765b137b2c17646002c4c514c77a3944ba88d4d38786ba953e679  libfreerdp-android.so
a7b4f084fcd0043dcca5f5359b703582bbc525d8f1efd3300c77b69372ded963  libfreerdp-client2.so
2d49f78d39fadb90b7b5ddfa47ab3f950375d0a1e728e9a9c54fd0b27c37eace  libfreerdp2.so
4bbee2458dde8bf22d47453194bf0a2e660718bb0760db4d0475eb496ce4149c  libssl.so
e6e1632420dab9dfbbfb0a62eed2b16b621d651d23ec96b467bd44c68b74504f  libswresample.so
69d26c392b3515ab4d509625e30d78093e5cbdce595a426c1e27d3860b598a5f  libswscale.so
22ae612df88d60c62d5057c4d9bd2688686afab4f44119fa243dea4babd2756f  libwinpr2.so
```

## Numbered FreeRDP patch inventory

Paths below are filenames in `remoteClientLib/jni/libs/` with `_freerdp_` and `.patch` omitted. Dispositions are migration hypotheses, not claims of 3.x equivalence. Inspect and link a replacement before dropping behavior; build adaptations must be reassessed for the new build system. There is no patch 17; both 24 and 25 have two patches.

| Patch | Current behavior or build adaptation | Likely 3.x disposition |
| --- | --- | --- |
| 01 relax_order_checks | Enables relaxed order checks | Review need and upstream setting; preserve if required |
| 02 use_different_ndk_for_openh264_build | Separate OpenH264 NDK | Old build adaptation; OpenH264 disabled, reassess if enabled |
| 03 remove_cert_ignore | Removes unconditional certificate ignoring | Preserve verification and prompts; inspect upstream |
| 04 wait_for_single_object_infinite | Changes input thread wait to 100 ms and installs fatal signal handlers | Redesign against 3.x lifecycle and crash handling |
| 05 add_gfx_progressive_alongside_gfx_and_gfxavc444 | Adds progressive graphics alongside GFX/AVC444 | Review negotiation and preserve fallback as needed |
| 06 ipv6_enable | Enables IPv6 in settings | Preserve IPv6; inspect upstream defaults |
| 07 scoped_storage_support | Redirects app external-files directory as drive | Adapt scoped-storage redirection |
| 08 update_compile_sdk_and_tools | Updates Android compile/target API and tools | Old Gradle build adaptation; reassess module requirements |
| 09 update_bookmark_base_rdp_file_support | Stores RDP filename in bookmark and parcel | Adapt RDP file behavior to 3.x adapter |
| 10 update_libfreerdp_rdp_file_support | Passes RDP file and optional connection arguments | Adapt RDP file argument generation |
| 11 customize_desktop_scale | Adds bookmark desktop scale and arguments | Preserve scale behavior in new adapter |
| 12 parallelize_android_build | Builds native install with 16 jobs | Old shell-build adaptation; reassess |
| 13 add_namespace_to_freerdpcore | Sets Android library namespace and min SDK | Reassess upstream Gradle module |
| 14 add_ldflags_to_freerdp_build | Sets 16 KiB maximum page size linker flag | Preserve alignment requirement in new toolchain; verify ELF |
| 15 add_ldflags_to_ffmpeg_build | Propagates architecture linker flags to FFmpeg | Reassess new FFmpeg build and ELF alignment |
| 16 fix_security_negotiation_settings | Adjusts NLA/TLS/RDP argument generation | Preserve security selection; inspect new parser |
| 18 log_dns_resolution_params | Logs resolver parameters and status | Review diagnostic need and sensitive logging |
| 19 add_glyph_cache_to_bookmark | Adds glyph cache bookmark/parcel setting | Preserve user setting in adapter |
| 20 toggle_glyph_cache_in_libfreerdp | Passes glyph cache setting | Preserve user setting in argument generation |
| 21 add_timeout_on_determining_h264_support_to_avoid_anr | Bounds H.264 support probe wait | Inspect 3.x probe and preserve UI responsiveness |
| 22 buffer_opensles_playback | Buffers playback and declares 150 ms latency | Preserve cushion until measured replacement validated |
| 23 use_selected_ndk_for_openssl | Sets selected NDK for OpenSSL | Old shell-build adaptation; ensure consistent new toolchain |
| 24 android_dynamic_resolution | Adds resize event and display-control monitor layout | Adapt or replace with verified 3.x equivalent |
| 24 treat_ffmpeg_gsm_alaw_mulaw_as_experimental | Filters GSM/A-law/mu-law in FFmpeg codec selection | Audit 3.x negotiation and actual codecs before carrying |
| 25 android_keyboard_repeat | Adds repeat to Java/JNI key events and flags | Preserve repeat semantics; inspect 3.x input API |
| 25 enable_optimize_build_gradle | Uses optimized default ProGuard configuration | Reassess upstream Android build/shrinker |
| 26 android_multimon | Adds shared-session monitor layout and region rendering | Adapt or replace with verified 3.x implementation; preserve panel behavior |

## Physical acceptance remains pending

No device was attached during baseline discovery. Installed signed APK version, Quest OS/build/API/page size, Windows build, negotiated graphics/decoder/audio, resolution and monitor count are unknown. Matched LAN and WireGuard workload measurements, responsiveness, frame pacing, CPU/memory and audio dropouts remain pending on the physical Quest and server. No installation, signing or publishing was performed.

## Local validation

`./gradlew --no-daemon :bVNC:testDebugUnitTest :remoteClientLib:testDebugUnitTest :questAdbHelper-app:testDebugUnitTest :aRDP-app:assembleRelease` passed: 26, 16 and 3 tests respectively, with no failures. The ARM64 unsigned APK SHA-256 is `763a9ba25e653613b92fa7f595368dc90902e8da564566f6dd72ecfa0d6e9107`. Compile/target SDK are 36, build tools 35.0.0. A fresh `:aRDP-app:dependencies --configuration releaseRuntimeClasspath` report also passed. Logs are local temporary files at `/tmp/quest-dependency-baseline/build.log` and `/tmp/quest-dependency-baseline/releaseRuntimeClasspath.log`; regenerate the report if unavailable. These checks do not validate a physical RDP connection or native rebuild.

The separate `:common:testDebugUnitTest` passed one test; its log is `/tmp/quest-dependency-baseline/common-test.log`.
The fresh graph resolves the local SQLCipher 4.5.4 AAR, AndroidX SQLite/SQLite KTX 2.2.0, Core 1.13.0, and Lifecycle runtime 2.6.2.
