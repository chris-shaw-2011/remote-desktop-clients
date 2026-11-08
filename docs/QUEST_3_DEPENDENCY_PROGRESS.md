# Quest 3 dependency update progress

## FreeRDP / WinPR 3.33.0

The stack uses verified FreeRDP 3.33.0 source and Android CMake ExternalDeps,
OpenSSL 3.5.9 and verified FFmpeg 9.0.2. FreeRDP 3 was committed as `cf65bfc0`. The slim embedded
module excludes upstream Room, bookmark persistence and standalone UI. The
[patch replacement map](FREERDP_3_PATCH_MAP.md) accounts for all 27 old patches.
Native CMake is 3.28.3 (ExternalDeps needs >=3.24), with existing NDK r25c,
API 23 and explicit 16 KB linker alignment.

Clean ARM64 build, all application release builds, runtime graph and all 61
local tests pass. Logs: `/tmp/quest-freerdp3-native-pass.log`,
`/tmp/quest-freerdp3-apps.log` and `/tmp/quest-freerdp3-unit.log`. New tests cover
eight certificate-pin cases and four bounded capability-probe cases. Static
inspection verifies 23 native methods and 25 callback sites against compiled
Java descriptors and native exports. The APK contains real `libfreerdp3`,
`libfreerdp-client3` and `libwinpr3`, with no 2.x counterparts. All 17 ARM64
libraries' DT_NEEDED entries resolve within the APK or Android system libraries.
Native configuration confirms MediaCodec, FFmpeg and OpenSL on, with
OpenH264/AAudio/direct UDP off; the buffered 150 ms marker is present.
Unchanged recipe/artifact checks skip rebuilding correctly.

APK ZIP alignment passes and new RDP libraries have 16 KB ELF LOAD alignment.
Three unchanged prebuilt inputs remain at 4 KB: `libc++_shared.so`,
`libgstreamer_android.so` and `libspice.so`; complete 16 KB device compatibility
is not claimed. App ID, version and target SDK remain unchanged. Physical Quest
connection, graphics, certificate/NLA, audio, independent/multi-monitor sessions
and sustained-use acceptance remain pending. Host-only old certificate pins
require renewed confirmation because their format does not bind a port.

## FFmpeg 9.0.2

Committed as `c9727166` after the clean native, all-application and 61-test
verification described below.

The tracked RDP recipe pins the official `ffmpeg-9.0.2.tar.xz` release archive,
SHA-256 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`.
Its detached signature was verified against upstream key
`FCF986EA15E6E293A5644F10B4322F04D67658D8`. The cache and ExternalDeps file URL
retain the archive's real `.tar.xz` name. Release headers identify avcodec
63.1.102, avutil 61.1.102, swscale 10.1.102 and swresample 7.1.102.

The codec whitelist follows FreeRDP 3.33.0's `h264_ffmpeg.c` and
`dsp_ffmpeg.c`: software H.264 decoding and its parser, PCM U8/U16LE and AAC
encoding/decoding, plus Opus decoding. The upstream nonexperimental DSP filter
excludes ADPCM, GSM, G.723 and A-law/mu-law. Native Opus encoding remains
experimental in FFmpeg 9, and FreeRDP does not enable its required compliance
mode; omitting that encoder avoids advertising unsupported microphone formats.
PCM and AAC microphone paths remain available where FreeRDP supports them;
MediaCodec can handle AAC separately. No external codec library is added.

The build retains avcodec, avutil, swscale and swresample and disables automatic
external-library discovery, all components before the whitelist, file formats,
protocols/network, filters, devices, programs and unrelated hardware accelerators.
FreeRDP's native MediaCodec backend stays enabled and bypasses FFmpeg video
when selected; software H.264 fallback remains available. FFmpeg's own MediaCodec
support is compiled without adding another video decoder. OpenH264 stays off,
and the buffered 150 ms OpenSL configuration is unchanged. The wrapper now
checks its staged JPEG/URI-parser libraries and derives its default NDK path
from the tracked version. Its existing fingerprint covers these changed inputs.

Clean ARM64 native compilation, every application release and all 61 local tests
pass (`/tmp/quest-ffmpeg9-native.log`, `/tmp/quest-ffmpeg9-apps.log`). The four
packaged FFmpeg libraries match clean output hashes and align to 16 KB; libavutil
reports 9.0.2, with no old 4.4.1 version marker. All 17 ARM64 libraries resolve
DT_NEEDED dependencies and the APK passes ZIP alignment. The codec list matches
the whitelist above, static JNI audit still passes 23 methods/25 callback sites,
and unchanged inputs skip rebuilding. Physical Quest acceptance remains pending for
native MediaCodec and deliberately selected software fallback, AVC420/AVC444,
resize/color/text rendering, independent and multi-monitor sessions, sound,
microphone if used, reconnects, and LAN/WireGuard. No attached device is available;
source inspection alone does not establish FFmpeg 9 runtime compatibility.

## Bouncy Castle 1.86 family

Provider and PKIX declarations are 1.86; TLS is pinned to 1.86.2. This narrow
patch exception follows the plan's newer-security-patch recheck: on November 8,
the [official download page](https://www.bouncycastle.org/download/bouncy-castle-java/)
still listed TLS 1.86.1, but the publisher's
[Maven Central metadata](https://repo.maven.apache.org/maven2/org/bouncycastle/bctls-jdk18on/maven-metadata.xml)
identifies 1.86.2 as latest/release (updated October 7), and its
[published POM](https://repo.maven.apache.org/maven2/org/bouncycastle/bctls-jdk18on/1.86.2/bctls-jdk18on-1.86.2.pom)
depends on utility 1.86. The target graph is provider/PKIX/utility 1.86 and TLS
1.86.2, using exact declarations and normal transitive resolution, without a
blanket force. The runtime graph confirms those exact versions, while separate
SpongyCastle core/provider remain 1.58.0.0. SnakeYAML 2.7 remains next.

The first parent application build compiled but found duplicate
`META-INF/LICENSE.md` resources in the four updated BC jars. Their license
contents are byte-identical (1,171 bytes; SHA-256
`0e01f1549c9022f406392ac2947d32223b7c2e977d21ea2f8c182fdeb4dae5fd`).
A shared application-packaging rule retains one copy of that exact path using
`pickFirsts`; no broad metadata exclusion is added. All release builds and all
61 tests pass. One freeaRDP packaging invocation required a retry; its isolated
retry and the final aggregate release build passed without another code change.
Logs: `/tmp/quest-bc-validation-final.log`, `/tmp/quest-bc-package-retry.log`,
`/tmp/quest-bc-all-apps-pass.log`.

The exact-version host smoke passes shipped RSA/EC PKCS8 and PEM recovery,
PKCS1/signature, X.509/fingerprint and BC JSSE TLS 1.2 initialization checks
(`/tmp/quest-bc-smoke-result.log`). It uses disposable keys and host adapters for
Android Base64/Log; no TLS connection or Android runtime acceptance is claimed.
The APK retains the single license entry and passes ZIP alignment.

Call-site review found explicit BC provider use for RSA/DSA/EC key recovery in
`PubkeyUtils`, lightweight ASN.1/RSA/AES/PBKDF2 use in SecureVNCPlugin classes,
and the BC JSSE provider in the VNC `TLSTunnelBase`. PEM key import still uses
ConnectBot and Android Base64; certificate parsing uses Java's X.509 factory.
No algorithm, stored-key format or TLS policy is changed. SpongyCastle,
ConnectBot, EdDSA and ECC dependencies remain unchanged. Direct native RDP TLS
uses OpenSSL 3.5.9; this BC update makes no RDP speed or NLA improvement claim.

A bounded temporary JVM smoke is prepared at `/tmp/quest-bc-smoke.py`: after
building, run `python3 /tmp/quest-bc-smoke.py 1.86.2`. It exercises the shipped
RSA/EC PKCS#8 and PEM recovery methods, SecureVNCPlugin PKCS#1 generation and
signature verification, certificate parsing/fingerprinting and BC JSSE TLS 1.2
initialization. Host Base64/log adapters stand in for Android; it does not prove
Android provider behavior, a server handshake or physical Quest acceptance.

## Android UI and lifecycle

The shared UI dependency declarations target Core/KTX 1.19.1, AppCompat 1.8.0, Material 1.14.0, Activity/KTX 1.13.0, Fragment/KTX 1.9.1, Lifecycle modules in the existing graph 2.11.0, Preference/KTX 1.2.1 and VectorDrawable 1.2.0. Transitive modules are aligned with constraints, not forced. Core KTX remains declared until the resulting graph and compilation are verified. The Core and Core KTX 1.19.1 AAR metadata requires compile SDK 37, so the shared compile SDK is now 37; target SDK remains 36 and minimum SDK remains 23. [Material 1.14.0 release notes](https://github.com/material-components/material-components-android/releases/tag/1.14.0) specify minimum SDK 23 and a library compile SDK of 35. No application lifecycle change is made. The aRDP CI workflow derives the platform from the shared compile SDK and uses `platforms;android-37.0`, the package identifier recorded by the installed SDK.

Root `assembleRelease`, the 46 focused common/bVNC/remoteClientLib tests and the aRDP runtime dependency report pass with the requested UI versions aligned. Kotlin remains 2.2.21, AGP 9.4.1 and Gradle 9.6.0. The ARM64 APK retains application ID/version and target 36/minimum 23, compiles against API 37, and passes ZIP alignment. Log: `/tmp/quest-ui-validation-sdk37.log`. Physical Quest acceptance remains necessary for panel focus and lifecycle, Back, keyboard and pointer input, resizing, dialogs, reconnects, independent sessions, and shared multi-monitor sessions on LAN and WireGuard.

## SQLCipher and SQLite

The shared minimum SDK is 23 (the separate Quest ADB helper remains 34). The database binding is SQLCipher Android 4.20.0 and AndroidX SQLite/KTX 2.7.1. The schema remains version 642; no automatic `cipher_migrate` is invoked. Zetetic's [migration guide](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/) describes the replacement binding and native loading; [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/sqlite) list 2.7.1. SQLCipher 4.5.4 and 4.20.0 both use the SQLCipher 4 default format; [Zetetic documents major-version compatibility settings](https://www.zetetic.net/sqlcipher/sqlcipher-api/#pragma-cipher_compatibility). Databases configured with older or nondefault formats require separate investigation.

Before testing an update, stop the app and preserve a consistent private app-data backup, including `databases/VncDatabase` and any journal/WAL/SHM files, relevant preferences and any platform-protected keys, using a device-appropriate backup method that preserves them. Protect that backup as sensitive data and verify it can be restored on a disposable device. Keep the original signed APK and its signing key. To roll back, stop the app, restore the complete pre-update backup with correct app ownership, and reinstall the original signed APK using a device-supported downgrade procedure without clearing app data; restore the backup again if installation modifies it. Do not assume an older APK can read a database changed by the new version. Production app-private data may be inaccessible to `run-as`; an XML export alone is not established as a complete backup of secrets. This procedure has not yet been tested on a device.

Device acceptance remains required: upgrade a same-signature install with disposable copies of plaintext and SQLCipher 4.5.4 encrypted databases, older supported schemas, multi-monitor connections and saved secrets; test password entry and failure, adding/changing/clearing passwords (including quotes), XML import/export, defaults, repeated opens/closes, and backup/restore and rollback. No connected Android device or hardware-accelerated emulator was available for native SQLCipher execution. JVM tests and successful compilation do not establish database migration or native loadability.

Local verification passed: root `assembleRelease` builds every application; `:common:testDebugUnitTest`, `:remoteClientLib:testDebugUnitTest` and `:bVNC:testDebugUnitTest` pass 46 tests including three new password control-flow regressions. The aRDP release graph contains only SQLCipher 4.20.0 and SQLite/KTX 2.7.1, with no old SQLCipher binding. The ARM64 APK retains `com.iiordanov.aRDP`, version `v6.5.0` / 116500 and target SDK 36, with minimum SDK 23. SQLCipher's ARM64 ELF LOAD segments align to 0x4000; the APK passes `zipalign -c -P 16 4`. Logs: `/tmp/quest-sqlcipher-validation.log` and `/tmp/quest-sqlcipher-password-tests.log`.

## Native RDP OpenSSL

The FreeRDP 2.11.7 recipe pins its own OpenSSL 3.5.9 source archive and SHA-256; the shared SPICE/GStreamer OpenSSL pin remains 1.1.1w. Android's OpenSSL shared libraries are rebuilt with the selected FreeRDP NDK and 16 KB ELF page alignment. OpenSSL's `no-module` configuration builds the legacy provider into libcrypto; FreeRDP's existing WinPR initialization loads both the default and legacy providers for its older RDP authentication algorithms. This does not change certificate checking or TLS security policy. The build clears old RDP outputs, including disabled prebuilt libraries, before rebuilding; its completion marker fingerprints the recipe, patches, ABI selection and compiler/linker flags. The prebuilt dependency downloader preserves an existing FreeRDP tree and never imports an archive build marker; first-time extraction clears any stale marker. The recipe uses its local CMake without replacing the Android SDK's CMake installation.

A clean ARM64 native rebuild passed with the final recipe (`/tmp/quest-openssl-native-final.log`), followed by project preparation, root `assembleRelease` and the 46 affected tests (`/tmp/quest-openssl-apk.log`). Native CMake reports OpenSSL 3.5.9; the rebuilt `libcrypto.so` identifies that version and contains the embedded legacy-provider initializer. FreeRDP links to the rebuilt `libssl.so`/`libcrypto.so`, whose SONAMEs match, and the APK's libcrypto SHA-256 exactly matches the rebuilt input (`3eaf9e0e1b02facd567bec23e5810b68dcce1734fcff360a1a6f30213f3104c9`). OpenSSL's ELF LOAD segments align to 16 KB and the APK passes ZIP alignment. Physical Quest testing remains outstanding: Windows TLS/NLA, trusted and self-signed certificates, changed-certificate rejection, reconnects, and LAN/WireGuard paths must be exercised on a device.
