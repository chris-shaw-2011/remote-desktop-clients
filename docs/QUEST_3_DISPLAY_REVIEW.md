# Quest 3 display quality review

Updated November 14, 2026. Implementation and local validation complete on
`master`; changes are uncommitted. Physical Quest/server acceptance remains.
User authorized focused fixes by Luna subagents, parent review, minimal code,
and a resumable record. Resume here before repeating discovery.

## Confirmed findings and assignments

| Area | Finding | Status / owner |
| --- | --- | --- |
| Local colors | RDP allocated `RGB_565`, discarding color precision even with a 32 bpp session | Reviewed: `ARGB_8888` for RDP; allocation regression passed |
| Redraw pacing | Both redraw overloads throttled at >16.6666 ms and delayed skipped updates by 100 ms | Reviewed: full-view `postInvalidateOnAnimation`; removed duplicated timers |
| Profile defaults | New connections requested 16 bpp and disabled font smoothing | Reviewed: 32 bpp / smoothing for new profiles; retain saved choices |
| Refresh preference | RDP activity did not request a higher refresh rate | Reviewed: highest exposed same-resolution window preference, no display mode ID changes |
| Resolution | Measured canvas pixels, even dimensions, 300 ms single-monitor resize; custom stays fixed and multi-monitor stays shared | Sizing regressions passed; reviewed fix accepts negotiated size without overwriting profile/reconnecting |
| Codec/color transport | Wrapper advertises AVC444 with AVC420/RFX/progressive fallbacks | Verified: native cache enables MediaCodec / FFmpeg / swscale, disables OpenH264 |

## Scope and constraints

This is an ordinary resizable Android 2D panel hosted by Horizon OS, not an
immersive OpenXR renderer. Its window pixels and refresh rate differ from the
headset's per-eye physical panels. Do not hardcode eye-buffer dimensions, add
OpenXR/Spatial SDK, force vendor properties, or claim guaranteed 90/120 fps.
The existing SDR RDP path should preserve 8-bit RGB; wide-gamut/HDR flags cannot
create missing source color information.

No AGENTS.md was found in the repository or ancestor paths. Working tree was
clean at start. Dependency migration is already present (FreeRDP 3.33.0 /
FFmpeg 9.0.2); do not restart it. Native source changes belong in tracked
`remoteClientLib/jni/libs/freerdp3-patches`, never only in extracted dependencies.

## Review checkpoint

All implementation tasks were completed by Luna (`gpt-6-luna`) agents and
reviewed by the parent. Production changes touch five files: 51 lines added,
58 removed. No dependency, configuration layer or schema was added. Four test
files add ten regressions. Final aggregate check passed; log
`/tmp/quest-display-final-build.log`. Resume with physical acceptance below.

For existing profiles, choose color depth 32, enable Font Smoothing, RDP Gfx and
RDP Gfx H264, and use Native Landscape/Portrait to follow the panel. Existing
profiles and saved default templates retain their stored choices. Custom
resolution remains deliberate fixed sizing. Multi-monitor sessions retain their
shared negotiated topology and scale individual panels locally.

The full-color local bitmap uses four bytes per pixel instead of two (about
8 MiB rather than 4 MiB at 1920x1080). CPU/GPU cost, temperature and battery life
at high refresh rates still require measurement. Rollback requires reverting
the five production-file changes and rebuilding; no database migration exists.

## Sources checked

- Android frame-rate guidance: https://developer.android.com/media/optimize/performance/frame-rate
- Android View invalidation API: https://developer.android.com/reference/android/view/View
- Android bitmap formats: https://developer.android.com/reference/android/graphics/Bitmap.Config
- Meta display guidance: https://developers.meta.com/vr/design/display/

## Local validation results

The first aggregate run passed production compilation but failed three new
refresh fixtures because Mockito could not mock Robolectric's instrumented final
`Display.Mode`. Luna replaced those fixtures with real reflective API-28 mode
construction. The intermediate run passed in 48 seconds. After the final
negotiated-size fix, **all 72 tests and release assembly passed in 41 seconds**:
44 bVNC, 24 remoteClientLib, 4 freeRDPCore, zero failures or skips. The command was:

```bash
./gradlew --no-daemon --max-workers=4 :bVNC:testDebugUnitTest \
  :remoteClientLib:testDebugUnitTest :freeRDPCore:testDebugUnitTest \
  :aRDP-app:assembleRelease
```

The server-size mismatch fix accepts negotiated dimensions for the current session, preserving
saved geometry and color instead of forcing CUSTOM and reconnecting.
Native `android_post_connect` initializes GDI before `OnSettingsChanged`, so the
negotiated framebuffer already exists; multi-monitor and subsequent resize
callbacks already accept dimensions without reconnecting.

`git diff --check` passes. Packaged manifest inspection confirms resizable
session activities and density/screen-size support, with no hardware-acceleration
opt-out. APK inspection confirms `com.iiordanov.aRDP`, version `v6.5.0` / 116500,
compile 37, target 36, and 17 native libraries exclusively for ARM64.

Final unsigned APK:
`aRDP-app/build/outputs/apk/release/aRDP-app-arm64-v8a-release-unsigned.apk`

SHA-256: `de5e157fc8c11b1d4bfcb1db7f01ad71c261a6a0b83c117d570aecd58b039d07`.
No native rebuild was needed: these fixes are Java settings/rendering changes.
Nothing was signed, installed, committed or published.

## Resume: physical acceptance

Permitted host ADB check returned an empty device list. Device performance,
negotiated codec, server frame production, network throughput and visual
acceptance remain unverified until exercised on the Quest 3 and RDP server.

1. Use the existing signing/release process before installation, retaining the
   installed app's signing identity to preserve its data.
2. Record Quest OS/build, app-window pixel dimensions, exposed display modes and
   actual refresh rate. A window preference is a scheduler hint; Horizon OS may
   expose a virtual display with fewer rates than the physical headset supports.
3. Select the quality settings above on existing profiles/default templates.
   Check fine colored text, gradients, photographs and a scrolling desktop;
   confirm negotiated AVC444 or identify the server-selected fallback/backend.
4. Resize native landscape/portrait windows, reconnect at a different window
   size, and verify custom dimensions stay fixed. Exercise a server-selected
   size that differs from the request: no reconnect or saved-mode conversion.
5. Exercise independent and shared-monitor panels, including an unfocused panel,
   closing/reopening a panel, and resizing without changing shared topology.
6. Measure sustained frame delivery, latency, memory, temperature and battery
   over LAN and WireGuard. Server frame production and network bandwidth can
   still limit the stream. Check audio/input/reconnects during the same workload.

Vsync scheduling tests verify the redraw request path, not physical frame
delivery. Source review and JVM tests do not establish headset color accuracy,
90/120 fps, decoder selection or end-to-end performance.
