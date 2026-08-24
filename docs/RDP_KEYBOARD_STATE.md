# RDP keyboard and Quest Meta-key state repair

Status: revised after physical Quest testing and locally validated on August 31,
2026. The lock/modifier revision and the combined signed Meta-key build still
need final physical validation.

## Objective

aRDP must keep Android's physical keyboard state and the remote Windows keyboard
state aligned during normal and rapid typing. Caps Lock must have two states,
Shift must invert Caps Lock normally, and a missed Shift release must not leave
subsequent text capitalized. On Quest, the physical Windows key must reach the
remote session only while an aRDP window has spatial focus; otherwise it must
retain Horizon OS's normal launcher behavior.

The change is limited to RDP input. VNC and SPICE keyboard behavior is outside
this repair.

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
- [ ] Verify both behaviors together using the final signed aRDP build.

## Implementation map

| File | Responsibility |
| --- | --- |
| `bVNC/src/main/java/com/iiordanov/bVNC/input/RemoteRdpKeyboard.java` | Resolves the physical Shift side and retains meta-state separately for every pressed RDP key. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RdpKeyboardMapper.java` | Tracks Android Caps Lock state and emits one complete remote keypress per change. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RemoteKeyboard.java` | Provides exact meta-state replacement after RDP resolves ambiguous physical Shift metadata. |
| `remoteClientLib/src/main/java/com/undatech/opaque/input/RemoteKeyboardState.java` | Tracks sent modifiers, physical Shift side, and per-key down-state. |
| `remoteClientLib/src/main/java/com/undatech/opaque/RdpCommunicator.java` | Reconciles remote modifier state in queue order and records directly forwarded modifier events. |
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
