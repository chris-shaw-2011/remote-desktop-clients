# Maintaining the personal aRDP fork

Prefer upstream implementations, including partial implementations, and adapt the
remaining personal behavior around them. Do not preserve duplicate personal
implementations merely because they already work. Keep actual upstream commits
in the branch history so the sync workflow can recognize integrated changes.

## Personal behavior to account for

| Area | Extension beyond upstream | Main entry points |
| --- | --- | --- |
| Personal builds | Signed ARM64 aRDP releases, native dependency rebuild, scheduled rebase | `.github/workflows/`, `PERSONAL_BUILD.md` |
| Quest panels | Independent connections and in-place picker replacement; per-handle FreeRDP event routing and cleanup | aRDP manifest, `ConnectionGridActivity`, `RdpSessionRegistry`, `RdpCommunicator` |
| RDP multi-monitor | 1-16 panels sharing one Windows session; count persisted in DB version 642 | `RemoteRdpConnection`, `RdpMonitorConnection`, `RdpMonitorLayout`, FreeRDP patch26 |
| Window resizing | Debounced dynamic resolution for single-monitor sessions | `RemoteRdpConnection`, FreeRDP patch24 |
| Keyboard | Modifier/lock synchronization, repeat handling, focused Windows-key capture | `RemoteRdpKeyboard`, `RdpKeyboardMapper`, `RemoteKeyboardState`, `SystemKeyCaptureService`, FreeRDP keyboard patch25 |
| Mouse and toolbar | Quest wheel normalization and speed control, stateless RDP wheel delivery, optional toolbar | `NormalizedScrollActivity`, `TouchInputHandlerGeneric`, `RemoteRdpPointer`, global preferences |
| Audio | OpenSL playback cushion and matching 150 ms declared latency | FreeRDP patch22, `docs/RDP_AUDIO_PLAYBACK.md` |
| Quest helper | Persistent wireless ADB and accessibility enablement | `questAdbHelper-app`, `ArdpApp` |
| Display and lifecycle | Font smoothing; return to connection picker after disconnect | `RdpCommunicator`, `RemoteCanvasActivity` |

The other files under `docs/` explain feature rationale and physical Quest
acceptance checks. The extracted FreeRDP source is ignored build output; keep
source changes in the numbered patch series instead.

## November 7, 2026 integration

The last successful scheduled run before the failure was on November 5 at
06:57 UTC. The first failed run was on November 5 at 20:03 UTC. Both that run
and the November 7 run failed while rebasing the personal concurrent-session
commit, in `ConnectionGridActivity.java`. The build job was skipped.

- [First failed run](https://github.com/chris-shaw-2011/remote-desktop-clients/actions/runs/42099773330)
- [Inspected November 7 run](https://github.com/chris-shaw-2011/remote-desktop-clients/actions/runs/42376041958)

Integration started from remote master `11975c843a228f9717145b20596477a7094d0e04`,
whose upstream base was `fd308d85`. The original local master `3238d38e` was
preserved; the working branch is `integrate/upstream-2026-11-07`.

Each of the following incoming commits was assigned to a fresh agent in order,
with a short shared context and narrowly scoped diff review. The branch was
rebased onto each successive upstream commit. Combined tests were reserved for
the final result rather than repeated for every clean rebase.

| Commit | Upstream change |
| --- | --- |
| `6ac82260` | Guard browser launches and show localized errors |
| `bf7bf086` | Gradle toolchain migration |
| `1c672e50` | AGP 9.4.1 and Gradle 9.6.0 upgrade |
| `9e80b100` | Bounded SPICE proxy resolution and logcat export |
| `9fabf844` | SPICE navigation scan-code tests |
| `94539dbb` | SPICE navigation and French layout corrections |
| `7496c952` | Layout generator filtering and alias safeguards |
| `85e8e62c` | French Dvorak layout |
| `a92a7ccd` | Stronger SPICE layout regression tests |
| `634657b9` | SPICE media/app scan codes and unmapped-button handling |
| `1a58d961` | Version 6.5.0 / build 116500 |
| `66b81d98` | Docker Compose v2 build instructions |

### Review and validation

The browser-launch conflict now retains upstream's `SecurityException` handling
around the personal in-place/document launch routing. Upstream's logging
constants and Gradle test configuration replace the corresponding personal
configuration. SPICE layout changes do not replace the separate RDP keyboard
mapper; the upstream generator, maps and tests are retained exactly.

All 125 files changed only by upstream match upstream exactly. The ten files
changed on both sides were reviewed for the remaining personal extensions.

Validation passed with JDK 21 and the upstream Gradle 9.6.0 / AGP 9.4.1 setup:

```bash
./gradlew --no-daemon :bVNC:testDebugUnitTest \
  :remoteClientLib:testDebugUnitTest \
  :questAdbHelper-app:testDebugUnitTest :aRDP-app:assembleRelease
```

- 45 unit tests passed: 26 in bVNC, 16 in remoteClientLib, and 3 in the helper.
- All 27 numbered FreeRDP patches applied to clean 2.11.7 source.
- The unsigned ARM64 APK reports `com.iiordanov.aRDP`, `v6.5.0`, build `116500`.
- Packaged document/task flags, accessibility service and update receiver were
  verified; the ARM64 split contains no other native ABI.
- The final diff passes whitespace checks with upstream CRLF endings allowed.

The local APK build reused existing native libraries after applying upstream's
FreeRDP Gradle patch to the extracted dependency. A full native rebuild and
release signing belong to the GitHub build workflow. Physical Quest/server
acceptance checks remain as described in the feature documents.

## Future integration procedure

1. Fetch both remotes and start from the current remote fork branch; scheduled
   rebases can rewrite commit IDs even when local source changes are absent.
2. Inspect the failed job logs before attributing a failure to conflicts.
3. Review each incoming diff against the personal behavior above. Adopt upstream
   helpers, settings and tests wherever they cover the same behavior.
4. Rebase the personal patch stack onto upstream. Resolve semantic overlap
   manually; do not apply a blanket ours/theirs strategy. In a rebase those
   labels also differ from an ordinary merge.
5. Check upstream ancestry, the remaining fork diff, native patch applicability,
   unit tests and the aRDP release build. Review packaged task flags and native
   feature markers when relevant. Physical Quest behavior still needs device
   testing.
6. Publish a reviewed rebase with an explicit expected old SHA in
   `--force-with-lease`, as the existing sync workflow does. Never overwrite a
   branch that changed while integration was in progress.
