# Concurrent aRDP sessions on Quest 3

Status: implemented with local unit, build, and packaged-manifest validation
through September 1, 2026. The in-place transition and full physical Quest 3
validation matrix remain outstanding.

## Objective

The personal aRDP build should be able to connect to multiple RDP hosts or user
accounts at the same time. Each connection should remain visible and live in a
separate resizable Quest panel. Starting, failing, or closing one connection
must not affect any other connection.

This behavior applies to every aRDP entry point: saved connections, shortcuts,
`rdp://` links, and `.rdp` files. It is intentionally scoped to aRDP and does not
change bVNC or aSPICE window behavior.

## Original failure and root cause

Before this change, the first RDP connection opened normally, but starting a
second connection disconnected the first. Several independent single-session
assumptions combined to cause that behavior:

- The inherited `RemoteCanvasActivity` manifest entry used
  `launchMode="singleInstance"`, so Android treated the remote canvas as a
  singleton activity rather than an independently launchable panel.
- The connection grid and every RDP document used the application's default task
  affinity. Reopening the launcher could therefore select an RDP document task
  and place the grid over its canvas instead of returning to a distinct launcher
  task.
- The saved-connection path used `startActivityForResult`. A document activity
  is an independent task and must not retain an activity-result relationship
  with the grid. On Quest, this prevented the expected new-document routing and
  allowed a subsequent selection to bring the existing RDP task forward.
- Every `RdpCommunicator` constructed a new FreeRDP `GlobalApp`, replaced the
  static `GlobalApp.sessionMap`, and therefore erased the process's knowledge of
  existing sessions.
- Every communicator installed itself as FreeRDP's single static event listener.
  The newest communicator consequently received callbacks that belonged to
  earlier native instances.
- `RemoteConnection.handler` was static, so the newest activity took ownership
  of connection messages, callback removal, and failure handling.
- Some confirmation and failure dialogs used process-global dialog helpers,
  allowing one activity to replace or dismiss another activity's prompt.
- The Android clipboard is process/device-global, so unrestricted
  remote-to-local clipboard updates from multiple live panels would race even
  after the connections themselves were isolated.

FreeRDP's native operations and event callbacks already include a `long`
instance handle. That handle provides the stable identity needed to separate
sessions without changing the external connection model or database.

## Design decisions

### One Android task and panel per RDP connection

Saved connections use the connection grid's existing Android task rather than
requesting an adjacent task. The grid starts an aRDP-only
`InPlaceRemoteCanvasActivity` without any new-task or new-document flags, then
finishes itself. Android consequently replaces the grid activity at the top of
the same task with the RDP canvas. Because Quest represents that task as one
panel, the session inherits the grid panel's exact position and size instead of
opening beside it.

The in-place activity subclasses `RemoteCanvasActivity`; it does not duplicate
the canvas or connection implementation. Its separate manifest declaration
exists only to use `documentLaunchMode="never"`. The normal exported
`RemoteCanvasActivity` retains `documentLaunchMode="always"`, standard launch
mode, and the session task affinity, so shortcuts, `rdp://` links, and `.rdp`
files still create independent document tasks. Android documents both activity
task stacks and document launch modes in its
[tasks and back-stack guide](https://developer.android.com/guide/components/activities/tasks-and-back-stack)
and [activity manifest reference](https://developer.android.com/guide/topics/manifest/activity-element).

The connection grid is itself now a standard, always-new document activity with
the picker-only `com.iiordanov.aRDP.connectionPicker` affinity. Reopening aRDP
therefore creates another picker task instead of finding the task that the
previous picker transformed into an RDP session. Selecting a connection then
transforms that new picker panel in place as well. The grid uses
`startActivity`, not `startActivityForResult`, because the finished picker must
not retain result coupling to the session.

This behavior is limited to direct saved connections in the paid aRDP package,
so bVNC, aSPICE, and freeaRDP retain their existing navigation model. If a saved
connection must first hand control to an external VPN client, aRDP preserves
the separate document launch because it cannot replace the picker with an RDP
canvas until that external flow returns.

Horizon OS does not expose a supported API for moving an existing Home panel to
a chosen side. Meta's Android multi-panel API only launches another activity
adjacent to the active one. The in-task replacement avoids that placement API
entirely and implements the requested result by keeping the same panel. See
Meta's [AOSP feature documentation](https://developers.meta.com/horizon/documentation/android-apps/features-overview/).

The manifest override is in the aRDP application rather than the shared bVNC
manifest. This avoids changing the other remote desktop applications built from
this repository.

A tabbed or multi-canvas activity was not chosen. Separate document tasks use
the operating system's existing Quest window management, require fewer UI
changes, and preserve the established one-canvas-per-activity structure.

### One process-wide FreeRDP dispatcher, many session owners

FreeRDP exposes one static event-listener slot for the process, so installing a
listener per communicator cannot support concurrency. `RdpSessionRegistry` is
therefore the one process-wide listener. It maintains a concurrent mapping from
native instance handle to the `RdpCommunicator` that owns that handle and routes
each callback only to that communicator.

The registry initializes FreeRDP's static `GlobalApp.sessionMap` only when it is
null. It never replaces a populated map. This preserves every active
`SessionState` when another connection is created.

FreeRDP also stores its UI event listener on each `SessionState`. Those listeners
remain session-local and continue to route graphics, clipboard, certificate,
and credential events through the owning canvas.

### Credential-retry handle replacement

An authentication retry can disconnect one native instance and create a new
one for the same `RdpCommunicator`. During that transition the registry:

1. Detaches the old `SessionState` UI listener.
2. Makes the new session the communicator's current session.
3. Removes event routing for the superseded handle.
4. Registers the replacement handle with the same communicator.
5. Retains cleanup ownership of the old handle until its terminal event arrives.

Non-terminal callbacks from a superseded handle are ignored. Its eventual
disconnect callback releases only that old native instance and cannot fail or
close the replacement session.

### Idempotent, handle-local cleanup

The registry tracks active native handles separately from callback listeners.
The first terminal callback removes the handle, notifies its current owner when
one exists, and schedules `GlobalApp.freeSession(handle)`. Duplicate terminal
callbacks for the same handle are ignored, preventing double-free behavior.

Explicit disconnect is also idempotent at the communicator level. It stops that
session's input executor, waits briefly for already queued input to finish, and
then disconnects only that communicator's native handle. Cleanup of one handle
does not clear either the registry or FreeRDP's shared session map.

### Activity-owned handlers and dialogs

`RemoteConnection.handler` is now an instance field. Removing callbacks while
closing one activity consequently affects only that activity. Clipboard
monitoring, the connection thread, input executor, SSH tunnel, decoder, canvas,
and FreeRDP state also remain owned by their corresponding connection instance.

Certificate, SSH host-key, encryption-warning, credential, and fatal-error
dialogs use the owning `RemoteCanvasActivity` fragment manager. A prompt in one
panel can no longer replace a process-global dialog belonging to another panel.

### Focus, graphics, clipboard, and lifecycle

Losing window focus does not pause the FreeRDP connection or graphics callback
path. Visible but unfocused panels therefore continue receiving live desktop
updates.

The Android clipboard cannot be namespaced per panel. Local-to-remote clipboard
monitoring already required the canvas to be foregrounded; remote-to-local
updates now follow the same rule. Only the focused panel may change the device
clipboard.

The existing activity lifecycle rule is preserved:

- `onPause` and `onStop` do not disconnect.
- Explicit Disconnect closes that panel's session.
- Closing or destroying a panel closes that panel's session.
- A terminal protocol failure closes only the failed panel's session.

No foreground service was added. Android process termination or force-stop can
still end all sessions in the process.

### Limits and compatibility

- There is no application-defined session limit. Horizon OS panel limits and
  available Quest memory are authoritative.
- No external API or connection database migration is required.
- Audio and microphone redirection remain configured per connection and may run
  concurrently, subject to Android, Quest, and server behavior.
- A server can still replace or reject sessions based on its own policies,
  especially when the same host and account are reused. The client no longer
  disconnects unrelated sessions itself.

## Implementation map

| Area | File | Responsibility |
| --- | --- | --- |
| aRDP task behavior | `aRDP-app/src/main/AndroidManifest.xml` | Declares always-new picker and external-canvas documents plus an in-place saved-connection canvas. |
| In-place canvas | `bVNC/src/main/java/com/iiordanov/bVNC/InPlaceRemoteCanvasActivity.java` | Reuses the existing canvas implementation under an aRDP-only same-task manifest policy. |
| Saved-connection launch | `bVNC/src/main/java/com/undatech/opaque/ConnectionGridActivity.java` | Replaces the paid-aRDP picker with its selected canvas and then finishes the picker. |
| FreeRDP dispatch | `remoteClientLib/src/main/java/com/undatech/opaque/RdpSessionRegistry.java` | Initializes shared state once, routes handle-keyed callbacks, and owns idempotent native cleanup. |
| RDP session owner | `remoteClientLib/src/main/java/com/undatech/opaque/RdpCommunicator.java` | Registers and replaces handles, ignores stale callbacks, and disconnects only its current instance. |
| Connection resources | `bVNC/src/main/java/com/iiordanov/bVNC/protocol/RemoteConnection.java` | Makes the handler instance-owned and restricts clipboard writes to the focused canvas. |
| Per-panel dialogs | `bVNC/src/main/java/com/iiordanov/bVNC/input/RemoteCanvasHandler.java` | Shows connection and failure prompts through the owning activity's fragment manager. |
| Unit coverage | `remoteClientLib/src/test/java/com/undatech/opaque/RdpSessionRegistryTest.java` | Tests independent routing, isolated disconnect, replacement handles, stale callbacks, and duplicate cleanup. |
| CI validation | `.github/workflows/build-ardp.yml` | Checks the packaged activity flags before signing. |
| User workflow | `PERSONAL_BUILD.md` | Describes launching and arranging multiple Quest panels and the operational limitations. |

## Implementation plan and completion status

- [x] Override only aRDP's canvas activity with standard, always-new-document,
  resizable task behavior.
- [x] Give picker documents and externally launched RDP documents distinct task
  affinities; keep direct saved sessions in the current picker task.
- [x] Make each aRDP launcher invocation a new picker document so it cannot
  reuse or cover an established session task.
- [x] Launch direct paid-aRDP saved connections in the picker's task and finish
  the picker, preserving that panel's position and size.
- [x] Preserve always-new document behavior for shortcuts, URI/file launches,
  and saved connections handed to an external VPN client.
- [x] Initialize FreeRDP's shared session map once rather than once per
  connection.
- [x] Install one process-wide event dispatcher keyed by native instance handle.
- [x] Track credential-retry replacement handles and ignore stale callbacks.
- [x] Make disconnect and native cleanup handle-local and idempotent.
- [x] Make `RemoteConnection.handler` instance-owned.
- [x] Keep clipboard monitors, input executors, SSH tunnels, and protocol state
  session-local.
- [x] Move connection and failure prompts to each canvas activity's fragment
  manager.
- [x] Continue graphics delivery for unfocused sessions while limiting clipboard
  synchronization to the focused panel.
- [x] Preserve the existing pause, stop, explicit-disconnect, and destruction
  lifecycle behavior.
- [x] Add local unit tests, packaged-manifest checks, and personal-build
  documentation.
- [ ] Complete the physical Quest 3 validation matrix below.

## Automated validation

The following checks passed after implementation:

```text
./gradlew --no-daemon :bVNC:testDebugUnitTest :remoteClientLib:testDebugUnitTest
BUILD SUCCESSFUL
./gradlew --no-daemon :aRDP-app:assembleRelease
BUILD SUCCESSFUL
```

The packaged ARM64 APK manifest reports:

```text
ConnectionGridActivity:
android:taskAffinity        com.iiordanov.aRDP.connectionPicker
android:launchMode          0x0          # standard
android:documentLaunchMode  0x2          # always

RemoteCanvasActivity:
android:taskAffinity        com.iiordanov.aRDP.rdpSession
android:launchMode          0x0          # standard
android:documentLaunchMode  0x2          # always
android:resizeableActivity  0xffffffff   # true

InPlaceRemoteCanvasActivity:
android:taskAffinity        com.iiordanov.aRDP.rdpSession
android:launchMode          0x0          # standard
android:documentLaunchMode  0x3          # never
android:resizeableActivity  0xffffffff   # true
```

The registry unit tests prove that:

- callbacks for sessions A and B reach only their respective listeners;
- registering B does not erase A;
- disconnecting A leaves B registered and able to receive events;
- callbacks from a superseded credential-retry handle do not reach the new
  session;
- a stale handle is released once even if its terminal callback is repeated.

The unit tests are run locally when the related code changes. The CI workflow
builds the release, verifies the application ID, version metadata, ARM64-only
native libraries, and packaged activity flags, then zip-aligns, signs, and
verifies the APK using repository secrets. Local validation stopped at the
unsigned APK because the release keystore is intentionally available only to
CI.

## Quest 3 validation matrix

Test two connections first, then three, using different hosts or users:

- [ ] Every connection opens in a separate Quest panel.
- [ ] A saved connection replaces the grid in the same panel, retaining its
  position and size.
- [ ] Reopening aRDP leaves every RDP panel unchanged and opens a new
  connection-grid panel.
- [ ] Selecting a different saved host from the reopened grid starts that host
  instead of bringing an earlier connection forward.
- [ ] All visible panels continue receiving live graphics.
- [ ] Focus, keyboard, pointer, and touch input move independently between
  panels.
- [ ] Resizing or temporarily unfocusing a panel does not disconnect it.
- [ ] Device clipboard updates come only from and are sent only to the focused
  panel.
- [ ] Explicitly disconnecting one panel leaves the others operational.
- [ ] Closing one panel leaves the others operational.
- [ ] Authentication failure in one panel leaves the others operational.
- [ ] Rejecting a certificate in one panel leaves the others operational.
- [ ] A saved connection creates another panel.
- [ ] A launcher shortcut creates another panel.
- [ ] An `rdp://` link creates another panel.
- [ ] Opening an `.rdp` file creates another panel.
- [ ] Concurrent audio behaves acceptably for the intended workflow.
- [ ] Microphone redirection behaves acceptably when enabled on more than one
  connection.

## Maintenance invariants

Future FreeRDP or lifecycle changes should preserve these invariants:

- Do not replace `GlobalApp.sessionMap` after it has been initialized.
- Do not install an individual communicator as FreeRDP's static event listener.
- Route native events by the callback's instance handle, not by whichever
  activity was launched most recently.
- Treat a credential retry as a handle replacement, not as a second owner of
  the same communicator.
- Ignore non-terminal events from superseded handles, but free each superseded
  native instance exactly once when it terminates.
- Never clear callbacks, dialogs, executors, tunnels, or clipboard state owned
  by another panel during connection cleanup.
- Do not tie graphics delivery to window focus. Use focus only for resources
  that are inherently shared, such as the Android clipboard.
- Keep the manifest override scoped to aRDP unless concurrent document tasks are
  deliberately adopted by bVNC or aSPICE as a separate feature.
- Keep the picker and externally launched RDP activity affinities distinct.
- Direct saved connections must target the in-place canvas without document or
  new-task flags, then finish the picker only after the canvas starts.
- Shortcuts, URI/file launches, and external VPN handoffs must retain their
  independent document-task behavior.
- Do not use `startActivityForResult` for an RDP session task.

If sessions later need to survive Android process reclamation, that is a new
architecture decision involving a foreground service and user-visible service
lifecycle. It is not part of this feature.
