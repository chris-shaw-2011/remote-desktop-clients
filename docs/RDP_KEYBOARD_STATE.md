# RDP keyboard and Quest Meta-key state repair

Status: root-cause revision implemented and locally validated on September 1,
2026. Physical Quest testing showed that the earlier Caps Lock and modifier
reconciliation introduced new state divergence. The plan below supersedes those
parts of the earlier repair while retaining the validated key-repeat and Quest
Meta-key work. Final Quest validation remains pending.

## Objective

aRDP must keep Android's physical keyboard state and the remote Windows keyboard
state aligned during normal and rapid typing. Caps Lock must have two states,
Shift must invert Caps Lock normally, and a missed Shift release must not leave
subsequent text capitalized. Holding a physical key must preserve Android's
initial press, repeat cadence, and final release in the RDP input stream without
an aRDP-owned repeat timer. On Quest, the physical Windows key must reach the
remote session only while an aRDP window has spatial focus; otherwise it must
retain Horizon OS's normal launcher behavior.

The change is limited to RDP input. VNC and SPICE keyboard behavior is outside
this repair.

## September 1 root-cause revision

The earlier repair treated Android event metadata as a second source of truth
alongside explicit physical key down/up events. That is the fundamental design
error. RDP already accepts the physical keyboard stream as ordered make,
repeat-make, and break events. Once a physical modifier make has been forwarded,
an ordinary key's metadata must not cause aRDP to release, replace, or move that
modifier. Windows owns interpretation of the forwarded virtual keys and its
keyboard layout.

Two current code paths directly explain the newly reported behavior:

- `RdpKeyboardMapper` intercepts Caps Lock and replaces the real down/up pair
  with a synthesized complete press whenever `KeyEvent.isCapsLockOn()` differs
  from a client-side nullable Boolean. The first observed value is treated as a
  transition even though the client has no prior Android or Windows lock state.
  This creates an independent toggle counter and can suppress the real Caps
  release or toggle Windows twice for one physical press.
- `RemoteKeyboardState.reconcileHardwareShiftState()` rewrites tracked physical
  Shift state from every non-Shift event's metadata. `RdpCommunicator` then
  synchronizes Windows to that inferred state before sending the ordinary key.
  If Android omits a Shift metadata bit on Enter, aRDP sends synthetic Shift-up
  before Enter despite having received and forwarded a real Shift-down. The
  server therefore sees plain Enter and submits the form.

The fact that reconnecting clears the behavior is consistent with this
client-owned state: a new communicator and mapper discard the inferred modifier
and Caps Lock values.

### Revised invariants

- A recognized physical key is forwarded as the corresponding Windows virtual
  key, preserving Android's down, repeat-down, and up order.
- Physical Caps Lock is an ordinary virtual-key down/up pair. aRDP does not
  count, deduplicate, or infer lock transitions.
- Only explicit physical modifier key events change remembered physical
  modifier state. Metadata attached to A, Enter, or any other ordinary key
  cannot manufacture or release a physical modifier.
- Event metadata may still describe modifiers for soft keyboards, pasted text,
  and the on-screen extra-key UI, because those sources do not necessarily emit
  standalone modifier events. That synthesis is kept out of the physical path.
- If Android focus/lifecycle loss prevents a final key-up from arriving, aRDP
  releases the keys it has actually forwarded as down. This is a bounded
  recovery at the input boundary, not inference from later character metadata.

### Revised implementation plan

- [x] Remove Caps Lock lock-state deduplication and cover one physical down/up
  producing exactly one remote down/up.
- [x] Stop reconciling physical Shift from ordinary key metadata and remove the
  per-key metadata cache that existed only to support that inferred state.
- [x] Keep physical modifiers active until their explicit key-up; combine them
  with synthetic on-screen modifiers without releasing them around each key.
- [x] Prefer virtual-key forwarding for recognized physical keys even when the
  Unicode preference is enabled; retain Unicode for IME/soft-text input and for
  physical keys that have no virtual-key mapping.
- [x] Track forwarded down keys and release the remaining set on window-focus
  loss, activity pause, and connection close.
- [x] Replace the old inferred-state assertions with regressions for Caps Lock
  down/up forwarding, Shift+Enter with incomplete Enter metadata, physical VK
  selection, explicit Shift release, and state cleanup.
- [x] Run focused unit tests, the aRDP debug build, packaged-ABI inspection, and
  diff checks.
- [ ] Perform the documented Quest validation matrix.

### Revised automated validation

The root-cause revision passes:

```text
./gradlew :remoteClientLib:testDebugUnitTest
  12 tests, 0 failures
./gradlew :bVNC:testDebugUnitTest --tests com.iiordanov.bVNC.input.SystemKeyCaptureServiceTest
  3 tests, 0 failures
./gradlew :aRDP-app:assembleDebug
git diff --check
```

The packaged debug APK contains the ARM64 FreeRDP libraries at
`lib/arm64-v8a/`. Its path and SHA-256 are:

```text
aRDP-app/build/outputs/apk/debug/aRDP-app-arm64-v8a-debug.apk
48a7bfa79bb327dc84e883afe152c139af7742abbfb10c97c840e350c98b72d6
```

## Reported symptoms

- Caps Lock appeared to have three states. After the first press letters were
  uppercase, but Shift did not make them lowercase. After the second press,
  Shift did not make lowercase letters uppercase. A third press was needed to
  restore normal Shift behavior.
- During fast typing, using Shift without touching Caps Lock could leave all
  subsequent letters uppercase.
- Physical testing of the first repair isolated another failure to right Shift:
  after producing the intended uppercase character, quickly typing the next
  character could capitalize that character as well. Left Shift did not exhibit
  the same behavior.
- The physical Windows key reached the RDP mapper and opened the remote Start
  menu, but Horizon OS also opened the Quest application launcher.
- A permanently enabled accessibility key filter stopped the Quest launcher and
  allowed remote forwarding, but also disabled the Windows key in every other
  spatial window, including after a headset reboot.
- Holding a physical key did not repeat it in the remote Windows session.

The second symptom distinguishes a remotely stuck Shift key from a Caps Lock
toggle. Both failures affect capitalization but require separate state repairs.

## Input-path context

Android key events pass through `RemoteClientsInputListener` and
`RemoteRdpKeyboard` into `RdpKeyboardMapper`. The mapper converts Android key
codes to Windows virtual keys. `RdpCommunicator` then queues those keys on one
RDP input executor before FreeRDP sends them to Windows.

Android reports both momentary modifier state and persistent lock state in each
`KeyEvent`. Windows maintains its own remote modifier and lock state, so the
client must keep the two state machines aligned. Preserving event order alone
cannot recover from an omitted or unusual key transition.

RDP keyboard input does not provide a separate persistent "this key is held"
notification that asks the server to start its own typematic timer. A held key
is represented by an initial make event, subsequent repeat make events, and one
break event. Android already supplies those repeat `ACTION_DOWN` events and
their platform-configured cadence through `KeyEvent.getRepeatCount()`. Forwarding
them is therefore distinct from aRDP synthesizing down/up pairs on its own
timer.

Quest routes the Windows key as Android's Meta key. Ordinary activity dispatch
still receives that event, but consuming it there does not prevent Horizon's
separate system-shortcut handler from opening the launcher. Android's
accessibility key-filter stage runs early enough to prevent that default action
and can forward the same event through aRDP's normal RDP input listener.

## Root causes

### Caps Lock counted transitions instead of state

`RdpKeyboardMapper` previously treated Caps Lock like an ordinary momentary
key: Android `ACTION_DOWN` became remote key-down and `ACTION_UP` became remote
key-up. It did not use `KeyEvent.isCapsLockOn()`.

That assumes every device delivers one conventional down/up pair for every
press. If Android delivers an alternating lock transition or one half is
missing, Android and Windows count different Caps Lock toggles and become
desynchronized.

### Generic Shift was incorrectly treated as left Shift

Android has separate bits for left Shift, right Shift, and the non-directional
meaning "one of the Shift keys is pressed." The code used the left-Shift bit as
its internal generic Shift mask. When a rapid right-Shift sequence contained
only Android's generic bit, the first repair cleared the tracked right Shift and
created a left Shift instead.

That side-changing inference explains why the remaining problem occurred only
with right Shift. Generic metadata from a physical key event must preserve the
side established by explicit Shift key events; it must not manufacture a left
Shift after right Shift has been released.

### Overlapping keys shared one down-state slot

`RemoteRdpKeyboard` stored the meta-state for every key-down in the single
`lastDownMetaState` field inherited from `RemoteKeyboard`. Fast typing naturally
overlaps events: right Shift, the shifted letter, its release, and the next
letter may all be in flight together. Each key-down overwrote the same field,
and the first key-up cleared it. A later key-up could therefore be translated
using another key's Shift state.

This is especially unsafe for Unicode input because the down and up halves can
be calculated as different characters. RDP now records down-state by key code
and consumes only the matching entry on key-up.

### A missed Shift release needed a recovery point

`RemoteKeyboardState` updated its physical Shift state only when it received a
Shift event. If a Shift-up event was omitted during fast overlapping input, the
client retained stale "hardware Shift down" state.

At the same time, directly forwarded physical modifiers were not recorded in
the remote modifier state used for synthesis. Later non-modifier events could
therefore neither detect that Windows still had Shift down nor send the missing
release. The result looked like Caps Lock even though the remote state was a
held Shift key.

### Quest spatial focus differed from Android activity focus

Horizon keeps aRDP's Android activity and window focused while the user moves
spatial focus to another panel. Consequently, `onWindowFocusChanged()` cannot
scope a permanently requested accessibility key filter: the listener remains
registered and Meta stays intercepted globally.

The standard accessibility interactive-window list does track Horizon's actual
spatial focus. The service listens for window-state and window-list changes and
checks the active or focused window's root package. It dynamically adds
`FLAG_REQUEST_FILTER_KEY_EVENTS` only when that package is aRDP, and removes the
flag as soon as another package is focused. `onKeyEvent()` repeats the package
check before forwarding and consuming a Meta event.

Quest does not expose a usable accessibility-service settings page. Initial
enablement therefore requires ADB to allow the app's restricted settings and
enable `SystemKeyCaptureService`; after that, the service survives reboot and
scopes itself without further ADB involvement.

### Held-key repeat state stopped at the Java/native boundary

`RemoteRdpKeyboard` detected Android repeat events, but
`RdpKeyboardMapper.KeyProcessingListener` exposed only the key and whether it
was down. `RdpCommunicator` and `LibFreeRDP` consequently could not distinguish
an initial down from a repeated down.

The pinned FreeRDP Android JNI code compounds that loss by setting
`KBD_FLAGS_DOWN` on every virtual-key down. In the RDP slow-path keyboard event,
that bit means the key was already down before this event: it is repeat state,
not the general opposite of `KBD_FLAGS_RELEASE`. Unicode events used no repeat
state at all. The repair must preserve Android's `repeatCount` through both
virtual-key and Unicode paths, emit no down/repeat bit for the initial make,
emit `KBD_FLAGS_DOWN` for repeat makes, and emit `KBD_FLAGS_RELEASE` for the
final break. FreeRDP fast-path encoding has no separate repeat flag, but still
preserves the repeated make events and final break.

The existing `KeyRepeater` helper is not part of the physical RDP key path and
must remain unused here; adding an aRDP delay or cadence would duplicate policy
already owned by Android and the physical keyboard stack.

## Implementation plan and completion status

- [x] Trace Caps Lock, Shift, and ordinary character events from Android through
  the RDP input queue.
- [x] Separate the persistent Caps Lock failure from the missed Shift-release
  failure.
- [x] Make Caps Lock respond to changes in Android's reported lock state rather
  than independently counting raw down/up events.
- [x] Send a complete Caps Lock press/release for each reported lock-state
  change and suppress the duplicate half of the same Android transition.
- [x] Record every physical modifier transition forwarded to Windows.
- [x] Reconcile physical Shift state from subsequent non-Shift key metadata so
  a later key can reveal a missed Shift release.
- [x] Before sending ordinary RDP input, compare the requested modifier state
  with the recorded Windows state and enqueue any required correction first.
- [x] Preserve a genuinely held physical Shift after each character while
  releasing synthesized modifiers after their character.
- [x] Keep Android's generic Shift meaning separate from left Shift for physical
  keyboard events, preserving an explicitly tracked side instead.
- [x] Stop generic metadata from recreating left Shift after right Shift has
  been released.
- [x] Replace RDP's shared `lastDownMetaState` with per-key down-state so
  overlapping key releases use their matching key-down state.
- [x] Sanitize the Shift bits used for character translation after physical
  side resolution, keeping Unicode key-down and key-up characters consistent.
- [x] Add regression tests for Caps Lock state changes, missed Shift-up
  recovery, retained physical Shift, right-Shift generic metadata, and
  overlapping key state.
- [x] Confirm that normal activity dispatch cannot cancel Horizon's independent
  Meta-key launcher shortcut.
- [x] Add an aRDP-only accessibility key-filter service that forwards Meta
  events through the focused remote canvas listener.
- [x] Retrieve interactive accessibility windows and dynamically request key
  filtering only while the active or focused root package is aRDP.
- [x] Remove key filtering when another Quest panel has spatial focus so the
  headset launcher continues to work normally there.
- [x] Add focused tests for Meta-key recognition and package matching.
- [x] Run the focused unit suite, build the aRDP debug APK, and inspect the diff.
- [x] Physically verify remote forwarding with the permanent filter and spatial
  focus scoping with a non-forwarding probe independently.
- [ ] Carry Android repeat state through `RdpKeyboardMapper` and its listener
  without changing synthetic custom-key behavior.
- [ ] Carry repeat state through `RdpCommunicator`, `LibFreeRDP`, and the JNI
  registration for both virtual-key and Unicode events.
- [ ] Correct the native RDP flags for initial make, repeat make, and break, and
  save that dependency change as a normal FreeRDP source patch.
- [ ] Add regression coverage proving an Android sequence with repeat counts
  becomes one initial down, repeat-marked downs, and one non-repeat release.
- [ ] Re-run the focused unit suite, aRDP build, patch applicability check, and
  diff checks.
- [ ] Verify both behaviors together using the final signed aRDP build.

## Implementation map

| File | Responsibility |
| --- | --- |
| `bVNC/src/main/java/com/iiordanov/bVNC/input/RemoteRdpKeyboard.java` | Resolves the physical Shift side and retains meta-state separately for every pressed RDP key. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RdpKeyboardMapper.java` | Tracks Android Caps Lock state and emits one complete remote keypress per change. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RemoteKeyboard.java` | Provides exact meta-state replacement after RDP resolves ambiguous physical Shift metadata. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RemoteKeyboardState.java` | Tracks sent modifiers, physical Shift side, and per-key down-state. |
| `remoteClientLib/src/main/java/com/undatech/opaque/RdpCommunicator.java` | Reconciles remote modifier state in queue order and records directly forwarded modifier events. |
| `remoteClientLib/jni/libs/*_freerdp_*.patch` | Preserves initial, repeat, and release state through FreeRDP's Android Java/JNI keyboard API. |
| `remoteClientLib/src/test/java/com/undatech/opaque/input/RdpKeyboardMapperTest.java` | Covers Caps Lock state-change de-duplication. |
| `remoteClientLib/src/test/java/com/undatech/opaque/input/RemoteKeyboardStateTest.java` | Covers missed Shift-up recovery, right-Shift side preservation, release recovery, and overlapping keys. |
| `bVNC/src/main/java/com/iiordanov/bVNC/input/SystemKeyCaptureService.java` | Requests the early Android key filter only while an aRDP accessibility window has spatial focus, then forwards Meta events to the remote canvas. |
| `bVNC/src/main/java/com/iiordanov/bVNC/RemoteCanvasActivity.java` | Registers the focused RDP input listener and retains ordinary dispatch as a fallback. |
| `aRDP-app/src/main/res/xml/system_key_capture_service.xml` | Declares Meta filtering and interactive-window retrieval capabilities plus focus-change event subscriptions. |
| `bVNC/src/test/java/com/iiordanov/bVNC/input/SystemKeyCaptureServiceTest.java` | Covers Meta recognition and aRDP package matching. |

## Automated validation

The following checks pass:

```text
./gradlew :remoteClientLib:testDebugUnitTest
./gradlew :bVNC:testDebugUnitTest --tests com.iiordanov.bVNC.input.SystemKeyCaptureServiceTest
./gradlew :aRDP-app:assembleDebug
./gradlew :aRDP-app:assembleRelease
git diff --check
```

The build produces `aRDP-app/build/outputs/apk/debug/aRDP-app-arm64-v8a-debug.apk`.

## Quest validation plan

Using the physical keyboard that exhibited the problem:

1. With Caps Lock off, verify unshifted letters are lowercase and Shift produces
   uppercase letters.
2. Turn Caps Lock on once. Verify unshifted letters are uppercase and Shift
   produces lowercase letters.
3. Turn Caps Lock off once and repeat the first check. Confirm a third press is
   never needed and the keyboard LED, Android, and Windows agree.
4. Rapidly type mixed-case text while overlapping Shift down/up with letter
   presses. After releasing Shift, verify the next unshifted letter is lowercase
   and remains so.
5. Repeat the rapid sequence separately with right Shift and left Shift. Confirm
   the character immediately after the intended uppercase character is
   lowercase for both sides.
6. Hold Shift across several letters to confirm the recovery logic does not
   release a modifier that is genuinely still held.
7. Disconnect and reconnect, then repeat with both Shift keys to
   confirm keyboard state starts cleanly in a new RDP session.
8. Focus an aRDP remote canvas and press the Windows key. Confirm that the
   remote Start menu opens and the Quest launcher does not.
9. Focus another Quest panel and press the Windows key. Confirm that the Quest
   launcher opens and the remote session receives nothing.
10. Reboot the Quest and repeat both focus cases to confirm the accessibility
    service persists without globally capturing Meta.
