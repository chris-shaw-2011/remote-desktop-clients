# RDP keyboard lock and modifier-state repair

Status: revised after physical Quest testing and locally validated on August 25,
2026. Physical Quest validation of the revision remains outstanding.

## Objective

aRDP must keep Android's physical keyboard state and the remote Windows keyboard
state aligned during normal and rapid typing. Caps Lock must have two states,
Shift must invert Caps Lock normally, and a missed Shift release must not leave
subsequent text capitalized.

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
- [x] Run the focused unit suite, build the aRDP debug APK, and inspect the diff.
- [ ] Verify the behavior with the physical keyboard on Quest.

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

## Automated validation

The following checks pass:

```text
./gradlew :remoteClientLib:testDebugUnitTest
./gradlew :aRDP-app:assembleDebug
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
