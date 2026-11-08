# Quest 3 runtime dependency update plan

Date: November 7, 2026. Status: proposed; no dependency updates implemented.

## Target and decisions

The primary target is the user's Meta Quest 3 on its latest installed stable
Horizon OS. Connections go directly to a home PC over the LAN or over an
already-established WireGuard connection. SSH tunnels are not used.

This plan covers runtime dependencies and the build changes required to consume
them. It does not authorize implementation, publishing, or headset installation.
Keep the existing Android panel application, application ID, signing identity,
saved connections, and personal Quest behavior.

Meta currently documents Horizon OS as Android 14 / API 34. A recent Horizon OS
version does not imply the latest Android API level. Record the actual headset
OS/build, API level, and page size during implementation; they were not measured
in this review. The current project compiles and targets API 36 and declares
minimum API 21. Do not lower targetSdk or raise it merely to match a library
version. Increase compileSdk only when published dependency metadata requires it.

Raise the shared minimum SDK to API 23 when the new database/UI dependencies are
introduced, consistently across the application and required library modules.
That drops Android 5.x support but is below the documented Quest OS baseline.
If the chosen resolved dependency graph requires a higher minimum, record and
apply that actual requirement; never override a library's minimum-SDK check.
There is no performance benefit from raising minSdk to 34 alone.

Sources: [Meta manifest/API guidance](https://developers.meta.com/vr/resources/publish-mobile-manifest/),
[current SQLCipher Android requirements](https://github.com/sqlcipher/sqlcipher-android).

## What could improve specifically on Quest 3

| Area | Evidence and expected benefit | Limit of the claim |
| --- | --- | --- |
| Hardware graphics decoding | FreeRDP 3.33.0's MediaCodec implementation includes low-latency configuration, output crop/stride handling, and bounded decoder waits. These are relevant to an Android headset rendering changing desktop resolutions. | The existing 2.11.7 build already enables MediaCodec and tries it before FFmpeg. A version bump does not newly enable hardware decoding. Actual decoder selection and performance must be observed. |
| Software fallback and audio codecs | FFmpeg 9 supplies current decoder, conversion, resampling, and correctness changes behind FreeRDP. | If a session uses FreeRDP's native MediaCodec backend, its video decoder bypasses FFmpeg. Benefits depend on negotiated codecs and fallback behavior. |
| Audio output | FreeRDP 3.33.0 includes an Android AAudio backend with explicit buffering. It is a candidate replacement for the fork's patched OpenSL ES backend. | AAudio can reduce device-side overhead/latency, but cannot eliminate network jitter or the need for a cushion. Retain the known buffering behavior until Quest measurements support a change. |
| Multiple movable panels | Current Activity, Fragment, Lifecycle, AppCompat, and Core libraries improve the foundation for configuration changes, dialogs, back handling, and lifecycle ownership. | They do not create new Horizon panel capabilities or fix application-level session ownership automatically. Several latest-release changes target APIs newer than the Quest OS. |
| Connection settings and database | The maintained SQLCipher Android library replaces an obsolete binding and supplies newer SQLite fixes. | Primarily reliability and maintenance; do not promise higher RDP frame rates. |
| Remote access over WireGuard | Updated FreeRDP remains useful for graphics/protocol correctness and security. Its experimental direct UDP transport may eventually help on a variable WAN path. | WireGuard itself uses UDP, but RDP inside it may still use TCP. FreeRDP's new UDP transport is experimental, server-dependent, and not a default change in this plan. |

Upstream evidence:
[FreeRDP 3.33.0 release](https://www.freerdp.com/2026/11/02/3_33_0-release),
[MediaCodec implementation](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/libfreerdp/codec/h264_mediacodec.c),
[AAudio implementation](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/channels/rdpsnd/client/aaudio/rdpsnd_aaudio.c),
[Android AAudio design](https://developer.android.com/ndk/guides/audio/aaudio/aaudio).

## Version targets and scope

These are stable release targets checked for this plan, not proof that the final
combination builds. Recheck for newer security patch releases at implementation
time, record exact versions/source hashes, and avoid floating `latest` inputs.
FFmpeg must remain on the requested **9.x** series; do not silently substitute
4.4.x as the completed upgrade.

| Dependency | Starting point | Planned target | Priority |
| --- | --- | --- | --- |
| FreeRDP / WinPR | 2.11.7 plus fork patches | 3.33.0 | Highest; native integration project |
| FFmpeg | 4.4.1 | 9.0.2 | Required; coordinated with FreeRDP 3 |
| OpenSSL for native RDP | 1.1.1w | 3.5.9 LTS | High; coordinated with native migration |
| SQLCipher Android | Local `android-database-sqlcipher-4.5.4.aar` | `net.zetetic:sqlcipher-android:4.20.0` | High maintenance/reliability |
| AndroidX SQLite / SQLite KTX | 2.2.0 | 2.7.1 | With SQLCipher |
| AndroidX Core / Core KTX | Declared 1.7.0; existing release report resolves 1.13.0 | 1.19.1 | Quest UI compatibility bundle |
| AppCompat | 1.7.1 | 1.8.0 | Quest UI compatibility bundle |
| Material Components | 1.13.0 | 1.14.0 | Quest UI compatibility bundle |
| Activity / Activity KTX | Existing report: 1.8.0 | 1.13.0 | Quest panel/back/lifecycle bundle |
| Fragment / Fragment KTX | Existing report: Fragment 1.5.4, KTX 1.3.6 | 1.9.1, aligned | Quest dialogs/lifecycle bundle |
| Lifecycle modules already in use | Existing report: 2.6.2 | 2.11.0, aligned | Supporting panel lifecycle and new FreeRDP Android module |
| Preference / Preference KTX | 1.2.0 | 1.2.1 | Small supporting settings fix |
| VectorDrawable modules | 1.1.0 | 1.2.0 | Supporting UI graph alignment |
| Bouncy Castle provider / PKIX / TLS / utility modules | Declared 1.80; some existing resolved components 1.80.2 | 1.86 consistently | Lower: shared crypto maintenance, not direct-RDP speed |
| SnakeYAML | 2.4 | 2.7 | Lower: configuration parsing, not Quest rendering |

Existing release reports are historical build evidence. Generate a fresh runtime
dependency report before implementation; do not assume those reports describe a
new build. Sources for version targets are collected at the end of this plan.

## Sequence and deliverables

1. Capture a reproducible baseline and map the fork's native patches.
2. Establish the SDK floor and migrate SQLCipher/SQLite as one bounded change.
3. Update the Android UI/lifecycle bundle as a separate change.
4. Integrate FreeRDP 3, OpenSSL 3.5 LTS, and FFmpeg 9 on a migration branch. They
   may be developed in separate commits, but ship only as a tested combination.
5. Evaluate the new MediaCodec and AAudio paths on Quest before changing defaults.
6. Update Bouncy Castle and SnakeYAML separately, after the higher-value work.
7. Complete Quest LAN and WireGuard acceptance, then prepare a reviewable release.

The newer upstream FreeRDP Android module already declares SQLCipher, Room,
Lifecycle, RecyclerView, and newer AndroidX dependencies. Inspect the whole
runtime graph when integrating it: importing that module must not accidentally
introduce a second database stack, extra bookmark persistence, unused UI, or
unrelated optional native codecs. Prefer upstream protocol/JNI implementations
and adapt the remaining personal behavior, consistent with
[UPSTREAM_MAINTENANCE.md](UPSTREAM_MAINTENANCE.md).

### 1. Baseline and compatibility inventory

- [ ] Record source commit, signed installed APK version, dependency graph,
  current native library versions and checksums, and actual compiled feature flags.
- [ ] Record Quest build/API/page size, PC Windows build, negotiated RDP graphics
  mode, decoder name/backend, resolution, monitor count, and audio format/backend.
  Redact credentials and network-sensitive details from collected diagnostics.
- [ ] Run the current focused tests and ARM64 release build before editing.
  Distinguish existing failures from upgrade regressions.
- [ ] Measure the same desktop workload on LAN and through WireGuard: text
  scrolling, window dragging, video, resize, and two/three panels. Record frame
  pacing, input responsiveness, process CPU/memory, and audio dropouts. Capture
  temperature/battery trends only under matched conditions, not as exact savings.
- [ ] Account for every numbered FreeRDP patch. Classify it as already upstream,
  still required, obsolete build adaptation, or requiring redesign. Link the
  replacement implementation before dropping any behavior patch.

Acceptance: a recorded baseline and patch-to-behavior map. Existing docs mark
some physical Quest acceptance as outstanding; do not turn those statements into
assumed baseline passes.

### 2. SQLCipher 4.20.0 and AndroidX SQLite 2.7.1

Entry points: `common/build.gradle`, `bVNC/build.gradle`, `common/aars/`,
`Database.java`, and all `net.sqlcipher` imports in `bVNC` and `common`.

- [ ] Raise the compatible SDK floor as described above. Replace the local old
  AAR references in both modules with the maintained Maven artifact. Update
  SQLite/KTX together. The SQLite wrapper alone does not update the native engine.
- [ ] Migrate `net.sqlcipher.database` to `net.zetetic.database.sqlcipher` and
  replace `SQLiteDatabase.loadLibs` with the current initialization contract.
  Adapt open helpers, raw SQL calls, cursor behavior, hooks, and close semantics.
- [ ] Preserve database schema/version and the stored monitor count unless an
  actual schema change is required. Do not confuse a library migration with an
  application-schema upgrade.
- [ ] Use disposable copies of representative existing databases to verify:
  unencrypted, master-password encrypted, old supported schema, and current
  multi-monitor settings. Exercise changing/clearing a password and failed opens.
  Retain explicit helper cleanup that avoids FinalizerDaemon hangs.
- [ ] Confirm old-format database readability from upstream compatibility rules
  and fixtures before using `cipher_migrate`. Do not rewrite every database on
  launch or expose user data to a speculative conversion.
- [ ] Verify encrypted/unencrypted import/export, saved connections, default
  connection settings, and saved secret handling. An older APK may not read a
  rewritten database; prepare and test a backup/restore rollback procedure.
- [ ] Inspect the packaged SQLCipher native library for ARM64 loadability and
  ELF/APK alignment. The old locally bundled AAR's alignment has not been measured.

Acceptance: install as an update over an existing app with the same signing key;
all saved settings remain usable, and repeated database access/close is stable.

### 3. Android UI and lifecycle dependencies for Quest panels

Entry points: Gradle declarations in `bVNC`, `remoteClientLib`, `pubkeyGenerator`,
and the FreeRDP Android adapter; `RemoteCanvasActivity`,
`InPlaceRemoteCanvasActivity`, `ConnectionGridActivity`, settings and dialogs.

- [ ] Update Core, AppCompat, Material, Activity, Fragment, and Lifecycle to the
  table targets. Align matching base/KTX modules. Prefer compatible direct
  declarations or constraints for transitive modules used by the app; avoid a
  blanket Gradle `force` across unrelated dependencies.
- [ ] Update Preference to 1.2.1 and VectorDrawable to 1.2.0 in the same UI bundle.
  These are supporting maintenance updates, not expected frame-rate improvements.
- [ ] Core now includes its Kotlin extensions; remove an empty/redundant
  Core-KTX declaration only if the resulting graph and source compilation agree.
- [ ] Let compatible dependency resolution bring required supporting versions;
  inspect the result for mixed Lifecycle/Fragment/Kotlin artifacts. Change build
  tools/compiler versions only when required by runtime dependency metadata.
- [ ] Preserve existing task affinities, document launch modes, in-place picker
  replacement, and all native-session ownership rules. A paused/unfocused panel
  must not disconnect merely because a library's lifecycle callback changed.
- [ ] Check dialogs, focus, hardware Back, the Quest virtual keyboard, insets,
  text selection, pointer coordinates, right-click, and wheel input while resizing.
  Apply API-gated fixes needed by the chosen versions; avoid a UI rewrite.
- [ ] Exercise close/reopen, authentication and certificate prompts in the
  focused panel, independent sessions, and the shared multi-monitor session.

Acceptance: existing panel/input behavior survives under the actual Horizon OS,
with no new clipping, focus theft, keyboard-capture, or session-lifecycle errors.
The benefit is current Android compatibility and upstream corrections, not a
guarantee that a specific present Quest bug has been diagnosed.

### 4. FreeRDP 3.33.0 migration

Entry points: `remoteClientLib/jni/libs/build-deps.conf`, `build-deps.sh`, the
numbered patch series, `settings.gradle`, `remoteClientLib/build.gradle`,
`RdpCommunicator`, `RdpSessionRegistry`, and `.github/workflows/build-ardp.yml`.

- [ ] Replace the obsolete 2.x Android shell-build assumptions with the 3.33.0
  Android CMake/ExternalDeps build. Upstream no longer supplies the old
  `scripts/android-build.conf` at the inspected tag; changing the version pin
  alone is insufficient. Inspect its supported NDK/CMake requirements first.
- [ ] Keep ARM64 as the personal release ABI. Use one compatible NDK/STL/API
  configuration across the new RDP dependencies, selected for actual runtime
  requirements. Audit all shipped native libraries for matching ABI and linkage.
- [ ] Make `WITH_MEDIACODEC=ON` explicit: the new upstream Android ExternalDeps
  file defaults it OFF. Retain FFmpeg support and keep OpenH264 OFF unless a
  demonstrated runtime need changes that decision.
- [ ] Use current upstream Android/JNI APIs where possible. Adapt bookmark
  settings, argument generation, callbacks, graphics buffer access, certificate
  prompts, connection lifetime, and native library loading to the 3.x API/ABI.
  Account for new Gradle root properties and native artifact paths.
- [ ] Preserve font smoothing, security negotiation, certificate verification,
  IPv6, glyph-cache control, desktop scaling, clipboard ownership, keyboard
  repeat/lock/modifier handling, independent sessions, dynamic resolution, and
  shared-session multi-monitor layout/rendering.
- [ ] Replace the old patch application scheme only where necessary; keep
  durable changes in tracked patches or adapter files, never only in ignored
  extracted dependency output. Patch failures must stop the build.
- [ ] Update CI's `libfreerdp-client2.so` and symbol/path checks for actual 3.x
  artifacts, not renamed old binaries. Preserve behavior checks with equivalents
  when an upstream implementation replaces a custom marker.
- [ ] Ensure downloaded prebuilt archives and preparation scripts cannot copy
  stale 2.x FreeRDP, old FFmpeg, or old OpenSSL over the rebuilt ARM64 stack.
  Make cache identity depend on source versions, patches, ABI, and build flags.
- [ ] Keep the current transport as the default. Evaluate direct RDP UDP only
  as a separately selectable experiment after basic acceptance; verify real
  negotiation and server support over both LAN and the existing WireGuard route.

Acceptance: native libraries and Java/JNI agree on versions and symbols; a real
Quest connection supports all personal features. FreeRDP 2.11.8 is not the final
target and is not a substitute for completing the 3.x migration.

### 5. OpenSSL 3.5.9 LTS for native RDP

- [ ] Pin the 3.5.9 source archive/tag and verified checksum in the native build
  used by FreeRDP 3. Do not accidentally use a host OpenSSL installation.
- [ ] Adapt 1.1.1-specific configure/download logic and NDK selection to the new
  upstream build. Record static/shared linkage and provider packaging; ship no
  second old OpenSSL from prebuilt RDP dependencies.
- [ ] Use current FreeRDP crypto APIs rather than carrying avoidable 1.1.1
  compatibility patches. Verify which old algorithms are needed for actual RDP
  authentication. If a legacy provider is required, configure and package it
  explicitly for that need; do not disable certificate checks or globally weaken
  TLS policy to make an initial connection succeed.
- [ ] Test the user's normal Windows/NLA connection, trusted and self-signed
  certificates, changed-certificate rejection, reconnects, IPv4/IPv6 as used,
  and LAN/WireGuard paths. Protect existing accepted-certificate behavior.

Acceptance: observed native version 3.5.9, correct TLS/NLA behavior and packaged
providers. This is security/reliability work; WireGuard does not replace RDP's
own authentication or encrypt/decrypt implementation. OpenSSL 4.1 is deliberately
outside this plan in favor of the supported 3.5 LTS branch.

### 6. FFmpeg 9.0.2

- [ ] Pin `n9.0.2` / the corresponding release archive and checksum in the new
  native dependency recipe. Expose it as a tracked build input, not an edit to
  ignored generated `android-build.conf` output.
- [ ] Build ARM64 `libavcodec`, `libavutil`, `libswscale`, and `libswresample`
  with the same Android toolchain/ABI as FreeRDP. Retain required audio decoding
  and microphone encoding where supported. Do not enable unused file protocols,
  filters, encoders, or optional libraries solely because the new build defaults
  enable them; establish required formats from actual RDP negotiation first.
- [ ] Compile FreeRDP's video and DSP paths against FFmpeg 9, addressing removed
  APIs using upstream implementations. The current 2.x DSP code still uses old
  `channels`/`channel_layout` fields and `av_get_default_channel_layout`; the
  inspected 3.33.0 source has modern `AVChannelLayout` handling. Audit remaining
  changes in packet/frame ownership, const codec APIs, pixel formats, resampling,
  and decoder flush/reconfiguration. Do not claim compatibility from this source
  inspection alone; prove it with a clean build and runtime checks.
- [ ] Remove stale FFmpeg 4 native artifacts from the final RDP package and
  check `DT_NEEDED`, SONAMEs, runtime version values, and JNI library load order.
  Confirm required 9.0.2 library versions against the official release page.
- [ ] Test native MediaCodec and a deliberately selected FFmpeg software fallback
  separately. Record which backend handles each stream; turning on FFmpeg's own
  MediaCodec support is not proof that FreeRDP selected hardware decoding.
- [ ] Test AVC420 and AVC444 where negotiated, progressive/non-H.264 fallback,
  high-resolution text, fine colored text, odd/non-aligned resize dimensions,
  desktop video, multi-monitor updates, and multiple independent decoder instances.
- [ ] Test stereo sound, silence/resume, sample-rate changes, reconnects, and
  microphone redirection only if used/enabled. Audit codec negotiation rather
  than blindly replaying the old experimental-codec patch.

Acceptance: the final APK loads FFmpeg 9.0.2 successfully, produces correct video
and audio on Quest, and retains a working fallback when a hardware decoder cannot
handle a stream. Performance results must identify the backend actually measured.

### 7. Quest MediaCodec and AAudio evaluation

- [ ] Measure new upstream MediaCodec low-latency behavior on the actual device.
  Check output stride/crop/color correctness, resize, decoder limits, and the
  aggregate resource cost of several sessions. Do not assume desktop resolution
  or decoder capacity from the headset's display specifications.
- [ ] Compare the new upstream AAudio backend against buffered OpenSL playback
  using the same content, network, volume, and panel count. Keep the existing
  150 ms buffering semantics for the initial migration or supply an equivalent
  measured policy before replacing patch22. Upstream AAudio's default cushion is
  100 ms when no explicit latency is requested; it is not equivalent by default.
- [ ] Test built-in speakers, any audio output actually used, short notification
  sounds, ten minutes of continuous speech/music, silence/resume, and two audible
  sessions. Check teardown and focus transitions. Reduce buffering only if both
  LAN and WireGuard measurements support it without recurring gaps.
- [ ] Select AAudio as the new default only if measured stability is at least
  as good and latency/overhead is useful. Otherwise keep the buffered backend
  and document the result; the dependency upgrade can still be successful.

No new Oboe dependency is planned: FreeRDP already supplies an AAudio backend.
No immersive rendering, OpenXR, Meta Spatial SDK, or new hardware-codec package is
needed just to update this existing 2D RDP client. Those are separate designs.

### 8. Bouncy Castle 1.86 and SnakeYAML 2.7

These remain smaller, independent maintenance changes. With no SSH tunnels and
direct native RDP, Bouncy Castle is not the connection's TLS backend and is no
longer a leading Quest optimization.

- [ ] Update Bouncy Castle declarations in `bVNC` and `pubkeyGenerator` together;
  confirm provider, PKIX, TLS, and utility artifacts resolve consistently to 1.86.
  Inspect the final runtime graph, not just the declarations. Old unused JARs in
  `libs/` are not automatically proof of packaged dependencies.
- [ ] Build and smoke-test the shared key/certificate code that remains in the
  app, without changing algorithms, stored data, or SSH functionality. Updating
  Bouncy Castle must not be represented as updating the separate old SpongyCastle
  dependency visible in the prior runtime report.
- [ ] Update SnakeYAML to 2.7. Verify `CustomClientConfigFileReader` against real
  custom configuration fixtures and invalid/recursive input. Confirm no use of
  the compact-constructor APIs removed upstream and preserve existing map shapes.
  This affects configuration parsing; do not claim a rendering benefit.

Acceptance: aligned crypto graph and unchanged supported config import behavior.

## Dependencies intentionally not assigned upgrade work

- **ConnectBot SSH / EdDSA / ECC libraries:** no direct-RDP or WireGuard benefit
  identified. Leave them unchanged; do not add an SSH migration or replace their
  crypto stack as part of this plan. If a new required module forces a compatible
  dependency adjustment, document that narrow requirement separately.
- **Zstandard and SPICE/GStreamer packages:** the inspected Zstandard use is in
  the VNC decoder; SPICE packages are unrelated to the requested RDP path. Do not
  refresh the whole shared native archive merely to make version numbers newer.
- **OpenH264:** disabled in the current RDP path; keep it disabled in the new
  build, whose defaults otherwise enable it.
- **Standalone WindowManager update:** the old version appears transitively, but
  current app code does not use AndroidX WindowManager APIs for Quest panels.
  Let necessary UI dependency updates resolve it; do not add foldable/embedding
  integration without a concrete Quest use case.
- **Standalone RecyclerView update:** the connection picker uses `GridView`, so
  there is no established Quest-picker benefit. If the new upstream FreeRDP
  module requires RecyclerView 1.4.0, retain its compatible requirement; do not
  rewrite the grid to justify an update.
- **Commons Validator, HttpCore, JCIFS, Google Play review/services:** no newly
  identified Quest 3 improvement justifies separate migration work. Compatible
  transitive changes required by planned updates remain in scope for review.
- **Toolchain/test dependencies:** no general refresh. Upgrade only the pieces
  required to build these runtime versions and keep those reasons explicit.

## Verification and release gates

Run the existing meaningful unit suites locally after their affected changes:
`:remoteClientLib:testDebugUnitTest`, `:bVNC:testDebugUnitTest`, and relevant
`:common:testDebugUnitTest` coverage. Add focused migration/compatibility tests
where behavior changes, especially database format and JNI/session contracts.
Keep the existing project policy of local unit testing; this plan does not add
unit suites to CI. Retain CI's native build, APK/manifest, ARM64, and signing checks.

Before release:

- [ ] Clean-build `:aRDP-app:assembleRelease` with the final pinned native inputs.
  Verify the packaged versions/features and all shipped ARM64 native linkage.
- [ ] Check native ELF segment and APK ZIP alignment. Record Quest's actual page
  size; 16 KB compatibility is useful packaging hygiene, not evidence that the
  current headset uses 16 KB pages or that it improves speed.
- [ ] Verify a signed in-place app update preserves database, master password,
  connection options, monitor count, system-key helper state, and app identity.
- [ ] Repeat the baseline workload on LAN and WireGuard, using the same server
  settings and resolution. If practical, repeat matched runs to avoid judging a
  change from one favorable Wi-Fi sample. Separate protocol improvements from
  changes in network conditions or the server's encoding policy.
- [ ] Cover one session, two independent sessions, and two/three monitors sharing
  one Windows session. Verify per-panel graphics, pointer offsets, keyboard repeat,
  modifiers/locks, Windows key capture, clipboard focus, audio, reconnect, and
  last-panel cleanup. Use the existing feature docs as the detailed matrix.
- [ ] Observe sustained use for at least 30 minutes with resize and focus changes;
  record ANRs/crashes, memory trend, decoder errors, audio gaps, and thermal effects.
- [ ] Publish a result table of before/after values and remaining limitations.
  A build pass does not satisfy physical Quest acceptance or prove a speedup.
- [ ] Keep a known-good signed APK and a tested settings/database recovery path.
  Prepare release artifacts from a reviewable branch; do not push to `master`
  during experimentation because its workflow automatically publishes releases.

## Source references

Local rationale and invariants:
[personal builds](../PERSONAL_BUILD.md),
[upstream maintenance](UPSTREAM_MAINTENANCE.md),
[audio buffering](RDP_AUDIO_PLAYBACK.md),
[concurrent sessions](CONCURRENT_RDP_SESSIONS.md),
[multi-monitor](RDP_MULTI_MONITOR.md),
[keyboard state](RDP_KEYBOARD_STATE.md),
[mouse wheel](MOUSE_WHEEL_SCROLLING.md).

Version/build references checked November 7, 2026:

- [FreeRDP 3.33.0](https://www.freerdp.com/2026/11/02/3_33_0-release),
  [Android module](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/client/Android/Studio/freeRDPCore/build.gradle),
  [native build](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt),
  [ExternalDeps](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/client/Android/cmake/ExternalDeps.cmake),
  [FFmpeg DSP adapter](https://github.com/FreeRDP/FreeRDP/blob/3.33.0/libfreerdp/codec/dsp_ffmpeg.c).
- [FFmpeg stable releases](https://ffmpeg.org/download.html),
  [FFmpeg 9 release notes](https://raw.githubusercontent.com/FFmpeg/FFmpeg/n9.0.2/RELEASE_NOTES).
- [OpenSSL releases and LTS dates](https://www.openssl-library.org/source/).
- [SQLCipher Android](https://github.com/sqlcipher/sqlcipher-android),
  [SQLCipher release announcements](https://www.zetetic.net/blog/),
  [AndroidX SQLite](https://developer.android.com/jetpack/androidx/releases/sqlite).
- [Core](https://developer.android.com/jetpack/androidx/releases/core),
  [AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat),
  [Material](https://github.com/material-components/material-components-android/releases/tag/1.14.0),
  [Activity](https://developer.android.com/jetpack/androidx/releases/activity),
  [Fragment](https://developer.android.com/jetpack/androidx/releases/fragment),
  [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle),
  [Preference](https://developer.android.com/jetpack/androidx/releases/preference),
  [VectorDrawable](https://developer.android.com/jetpack/androidx/releases/vectordrawable).
- [Bouncy Castle releases](https://www.bouncycastle.org/download/bouncy-castle-java/),
  [SnakeYAML changes](https://raw.githubusercontent.com/snakeyaml/snakeyaml/master/src/changes/changes.xml).

No headset benchmarks, migration builds, database conversions, dependency edits,
or release actions were performed while writing this plan.
