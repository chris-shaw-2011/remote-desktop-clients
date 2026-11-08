# Quest dependency implementation resume log

Updated November 8, 2026. Branch: `dependency/quest-3-runtime-updates`.
Implementation and local validation are complete. Do not restart the dependency
migrations. Read `QUEST_3_DEPENDENCY_PROGRESS.md` for versions, evidence and limits.

## Completed commits

| Commit | Update |
| --- | --- |
| a60c76f7 | Baseline and 27 old native patches inventoried |
| 69c86295 | SQLCipher4.20 / SQLite2.7.1 / shared minimum SDK23 |
| 9ffa4937 | UI/lifecycle bundle; Core requires compile37, target stays36 |
| f241056c | RDP OpenSSL3.5.9 LTS |
| cf65bfc0 | FreeRDP/WinPR3.33.0 and slim Java/JNI integration |
| c9727166 | FFmpeg9.0.2 with RDP codec whitelist |
| d482afd2 | Bouncy Castle provider/PKIX/utility1.86, TLS1.86.2 |
| Commit containing this log | SnakeYAML2.7 plus five real-reader compatibility tests |

Every dependency update was delegated, reviewed, verified and committed before
starting the next dependency. The complex FreeRDP update used focused native and
Java workers after an initial broad worker did not finish; its final integration
was clean-built and committed only after all checks passed.

## Verified final state

- Clean ARM64 native stack: FreeRDP3.33.0, OpenSSL3.5.9, FFmpeg9.0.2.
- Every application release clean-builds with four Gradle workers.
- All66 local tests pass: common1, bVNC34, remoteClientLib24, core4, helper3.
- Static JNI audit:23 native methods and25 callback sites agree.
- All17 packaged ARM64 libraries resolve DT_NEEDED entries; no old FreeRDP2.
- Native version strings and clean-output hashes match the final APK. New RDP
  libraries align16KB; ZIP alignment passes. Three unchanged prebuilt inputs
  remain4KB: libc++_shared, GStreamer, SPICE.
- App ID/version/task flags retained; compile37, target36, minimum23.
- BC runtime graph and actual shared key/certificate host smoke pass. Separate
  SpongyCastle remains1.58.0.0; SSH and unrelated native stacks were not refreshed.
- Production YAML reader unchanged; shipped fixture, invalid/recursive input and
  alias-limit behavior tested with2.7.

Final clean command:

```bash
./gradlew --no-daemon --max-workers=4 --stacktrace clean \
  :freeRDPCore:testDebugUnitTest :common:testDebugUnitTest \
  :remoteClientLib:testDebugUnitTest :bVNC:testDebugUnitTest \
  :questAdbHelper-app:testDebugUnitTest assembleRelease \
  :aRDP-app:dependencies --configuration releaseRuntimeClasspath
```

The default-worker incremental aggregate build had packaging-worker failures in
unused variants; the clean four-worker build passed without a production-code
fix. Do not claim an identified cause. aRDP itself also built in earlier default
worker checks. CI still builds only the personal ARM64 aRDP artifact.

Latest successful logs: `/tmp/quest-final-clean-build.log`,
`/tmp/quest-ffmpeg9-native.log`, `/tmp/quest-bc-smoke-result.log`.
Native/JNI audit helpers and JSON evidence remain in `/tmp/quest-*`; these are
local temporary evidence, not required application source. Native recipe and
checksums are tracked under `remoteClientLib/jni/libs`; its unchanged-input
skip and all required artifact checks were verified. Do not edit running native
scripts or patch inputs. Parent should run privileged Gradle/network commands;
agent escalation requests previously stalled.

Final unsigned APK:
`aRDP-app/build/outputs/apk/release/aRDP-app-arm64-v8a-release-unsigned.apk`

SHA256: `9dfbf456bde41a86df026a0ee0331572e01730ab1fb18295acfa52bd5b41244f`.

## Remaining acceptance, not unfinished dependency edits

No device was connected at the final adb check. Quest OS/API/page size, signed
in-place update/database/backup/rollback, real Windows TLS/NLA and changed-cert
rejection, MediaCodec/software fallback, sound, focus/input/resizing, independent
and shared multi-monitor sessions, LAN/WireGuard and sustained-use measurements
remain pending. Preserve the original signed APK and app data before those tests.
No FPS, latency, battery or thermal improvement has been measured. Host-only
legacy certificate pins need renewed confirmation; exact host+port pins can migrate.

Buffered OpenSL150ms remains selected. AAudio, direct RDP UDP and OpenH264 remain
off. No native SPICE/GStreamer refresh, Room/second DB or UI rewrite was added.
Nothing was pushed, signed, installed or published. Do not do those actions
without the user's instruction. Preserve the original untracked
`docs/QUEST_3_DEPENDENCY_UPDATE_PLAN.md`; it is not an uncommitted code change.
