# Quest dependency implementation resume log

Updated November 8, 2026. Branch: `dependency/quest-3-runtime-updates`.

User requires sequential dependency agents, parent verification and a separate
commit for each update. Prioritize ARM64 aRDP; other apps must build with minimal
unrelated changes. Do not push: master pushes publish releases. Preserve the
user's untracked `docs/QUEST_3_DEPENDENCY_UPDATE_PLAN.md`.

## Completed

| Commit | Update | Verification |
| --- | --- | --- |
| a60c76f7 | Baseline, all 27 old native patches inventoried | 46 original tests, aRDP release, fresh graph |
| 69c86295 | SQLCipher4.20 / SQLite2.7.1 / min23 | All app releases, 46 affected tests, graph, ARM64 library/alignment |
| 9ffa4937 | Android UI/lifecycle bundle | All app releases, 46 affected tests, graph; Core requires compile37, target stays36 |
| f241056c | Native RDP OpenSSL3.5.9, separate from unchanged SPICE pin | Clean ARM64 build, all app releases, 46 affected tests, packaged hash/linkage/alignment |
| cf65bfc0 | FreeRDP/WinPR3.33.0, slim module, native ports and certificate/probe tests | Clean ARM64 build, all app releases, 61 tests, JNI23 methods/25 callbacks, graph, APK/native audit |

Native FreeRDP3 is committed as `cf65bfc0`. FFmpeg9.0.2 recipe changes are now
prepared but await parent clean build and packaging verification before their
separate commit. No signing, installation or publishing occurred.
Physical Quest/database/Windows/NLA/decoder/audio/LAN/WireGuard acceptance remains
pending. No Android device was connected.

## Exact next work

1. FreeRDP3 is committed. Patch context lines have intentional leading spaces:
   run whitespace checks excluding `*.patch`, and retain strict patch application
   checks. Do not alter patch context just to satisfy generic whitespace lint.
2. Verify the prepared FFmpeg9.0.2 update with a clean native build, all app
   releases, affected tests and packaged versions/linkage, then commit before the
   next agent. Codec decision and pending physical acceptance are documented in
   `QUEST_3_DEPENDENCY_PROGRESS.md`; no FFmpeg4/8 fallback is permitted.
3. Delegate Bouncy Castle next, then SnakeYAML2.7, each verified and committed.
   Official BC page showed provider/PKIX/utility1.86 but TLS1.86.1; recheck that
   patch release under the plan's security-update instruction. Leave SpongyCastle
   and unrelated SSH packages unchanged.
4. Finish result/acceptance documentation and keep this resume log current.

## Implementation and evidence pointers

Read `QUEST_3_DEPENDENCY_PROGRESS.md` and `FREERDP_3_PATCH_MAP.md` for exact changes.
The old2.x patch series was removed after mapping all replacements. Native recipe:
`remoteClientLib/jni/libs/build-freerdp3.sh`, called by `build-deps.sh`; tracked
pins in `build-deps.conf`, build patch `freerdp3-build.patch`, Java patch
`remoteClientLib/freerdp-adapter/java.patch`, C ports `freerdp3-patches/`.
`freerdp-client/` is the slim `:freeRDPCore` module; no Room/second database/UI.

Native rebuild from `remoteClientLib/jni/libs`:
`ANDROID_ABIS=arm64-v8a FORCE_FREERDP_BUILD=true ANDROID_SDK=/home/localuser/Android/Sdk ./build-deps.sh -j 16 freerdp`.
Parent runs privileged commands: agent approval requests previously stalled.
Freeze script/patch inputs during builds; do not edit a running Bash script.

Latest successful logs: `/tmp/quest-freerdp3-native-pass.log`,
`/tmp/quest-freerdp3-unit.log`, `/tmp/quest-freerdp3-apps.log`.
Unit counts: common1, bVNC29, remoteClientLib24, freeRDPCore4, helper3 =61.
`/tmp/quest-native-audit.py` checks APK ARM64/linkage; `/tmp/quest-jni-audit.py`
checks JNI exports and Java descriptors. All17 packaged libraries resolve needed
libraries. Three unchanged prebuilt libs still have4KB ELF LOAD alignment:
libc++_shared, GStreamer, SPICE; do not refresh unrelated stacks or claim full16KB
compatibility. New RDP libraries and APK ZIP alignment pass.

Native3.33 uses NDKr25c plus a tiny private MediaCodec keyframe bit constant missing
from old headers (NDK28 confirms value1), system CMake3.28.3 (>=3.24 required), API23.
It keeps buffered OpenSL150ms; AAudio and direct UDP remain off. All56 baseline
keyboard integer values match. H264 probe is lazy/one-shot/bounded, after class init.
Known_hosts2 migration trusts only exact host+port+fingerprint matches; host-only
legacy pins prompt; no old certificate files are deleted.

Verified archives in `/tmp` and `remoteClientLib/jni/libs/tar/rdp`:

- FreeRDP3.33 SHA25680cbcf17a7de00909f2be805c7d93764596cd712046addc18c30594137c0b879;
  pristine `/tmp/freerdp-3.33.0`.
- OpenSSL3.5.9 SHA256603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a.
- **Prepared FFmpeg9.0.2** official `/tmp/ffmpeg-9.0.2.tar.xz`, SHA256
  8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e.
  Detached signature verified with upstream key
  FCF986EA15E6E293A5644F10B4322F04D67658D8. GitHub n9.0.2 tar also exists in /tmp.

Previous focused workers `/root/freerdp_native_port` and
`/root/freerdp_java_adapter` completed their changes. No worker is needed to redo
FreeRDP. Original broad worker produced only an inventory; do not restart it.
