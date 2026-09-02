# aRDP multi-monitor support on Quest

Status: implemented locally on September 2, 2026; Quest/server acceptance remains pending.

## Objective

Add physical-mouse connection management and true RDP multi-monitor sessions to
the personal `aRDP-app` build. A saved connection can request 1-16 monitors. A
multi-monitor launch must create one independently movable Quest panel per
monitor while using one FreeRDP connection and one remote Windows login session.

Launching the same bookmark repeatedly is explicitly not the implementation:
that creates independent RDP sessions and can cause the server to reconnect or
disconnect earlier clients.

## Fixed behavior

- Existing connections default to one monitor and retain current behavior.
- The connection editor stores a monitor count from 1 through the RDP protocol
  maximum of 16.
- Monitor 1 is primary. Equal-sized monitors are negotiated in a contiguous
  horizontal logical row. Quest panels remain independently positionable in
  physical space.
- The saved width and height describe one monitor. Automatic geometry uses the
  first panel's initial canvas dimensions.
- Multi-monitor sessions do not issue the existing single-monitor dynamic
  resize request when an individual Quest panel changes size; each panel scales
  its assigned monitor locally.
- Closing a monitor panel detaches that view. The shared session disconnects
  when the final panel closes. Explicit Disconnect closes the whole group.
- A remaining panel can restore missing monitor windows.
- Only the focused panel may update the Android clipboard or own interactive
  authentication and certificate prompts.

## Implementation checklist

### 1. Mouse connection actions

- [x] Extract the existing long-press connection actions into one reusable
  Edit/Delete/Shortcut menu method.
- [x] Invoke the same method from an Android context click on each recycled grid
  item so physical mouse right-click works without changing touch behavior.

### 2. Persisted connection setting

- [x] Add `RDPMONITORCOUNT` to `AbstractConnectionBean`, including table
  creation, values, cursors, content-value import/export, and accessors.
- [x] Default new and existing connections to one monitor.
- [x] Upgrade the connection database from 641 to 642 with `DEFAULT 1`.
- [x] Add a 1-16 monitor-count control to the aRDP advanced display section and
  include it in the editable default-connection template.

### 3. Monitor launch group

- [x] Add intent keys for a generated group ID, monitor index, and monitor count.
- [x] For a saved connection, keep monitor 1 in
  `InPlaceRemoteCanvasActivity` and launch monitors 2-N as normal aRDP document
  activities with the existing RDP task affinity.
- [x] Guard sibling intents so they attach to the existing group instead of
  recursively spawning more panels.
- [x] Give each task a monitor-numbered title.

### 4. FreeRDP monitor negotiation and rendering

- [x] Add a numbered FreeRDP patch after the existing dynamic-resolution patch.
- [x] Extend patched bookmark/JNI settings with monitor count and populate
  `UseMultimon`, `MonitorCount`, and `MonitorDefArray` before connect.
- [x] Validate 1-16 monitors and a maximum combined dimension of 32,766 pixels.
- [x] Negotiate one combined virtual desktop with monitor 1 at `(0,0)` and each
  following monitor immediately to its right.
- [x] Add a JNI graphics-region copy operation that accepts independent source
  and destination origins so each Android canvas stores only its monitor.

### 5. Shared Java session

- [x] Add a group registry distinct from the native-handle-based
  `RdpSessionRegistry`.
- [x] Start one `RdpCommunicator` and native FreeRDP instance per group, attach
  one canvas/handler target per monitor, and replay current geometry to targets
  that attach after connection.
- [x] Intersect combined framebuffer updates with each monitor rectangle, copy
  the intersection into the target bitmap, and invalidate only that region.
- [x] Translate local pointer coordinates by the monitor origin while keeping
  keyboard input serialized through the shared communicator.
- [x] Route credentials, certificates, and clipboard through the focused target;
  fan connection state and per-target first-frame state out to every panel.

### 6. Lifecycle and recovery

- [x] Detach one target when its activity is destroyed and disconnect only when
  the last target leaves.
- [x] Make explicit Disconnect terminate and finish every activity in the group.
- [x] Add Restore monitor windows for any missing indices.
- [x] Save the connection thumbnail from monitor 1 only.
- [x] Keep unrelated concurrent RDP connection groups isolated.

### 7. Verification

- [x] Add local unit coverage for monitor-count limits, update intersections,
  and pointer offsets. Group lifecycle and Android context-click behavior remain
  part of the physical acceptance matrix below.
- [x] Run existing remote-client unit tests locally; do not add them to CI.
- [x] Force an ARM64 FreeRDP rebuild and assert that the patched Java/JNI surface
  is present.
- [x] Build the unsigned aRDP release APK and retain the existing CI checks for
  picker/canvas tasks, application ID, version, ABI, packaging, and signing.
- [x] Inspect the final diff for unrelated or generated dependency artifacts.

## Quest and server acceptance matrix

- [ ] Right-clicking a saved connection offers Edit and Delete; touch long-press
  still works and deletion still asks for confirmation.
- [ ] A one-monitor connection opens exactly one panel and behaves as before.
- [ ] Two- and three-monitor connections open the requested number of movable
  Quest panels but create only one native/server RDP session.
- [ ] Windows Display Settings reports the requested monitor count.
- [ ] Graphics remain live in focused and unfocused panels.
- [ ] Pointer, click, wheel, keyboard, scaling, and clipboard behavior is correct
  in every monitor panel.
- [ ] Remote windows can cross the horizontal logical monitor boundaries.
- [ ] Resizing a Quest panel scales that view without changing remote topology.
- [ ] Closing/restoring one monitor leaves the other monitors connected.
- [ ] Explicit Disconnect and final-panel close release the shared session once.
- [ ] A second unrelated connection group remains fully independent.
- [ ] Installing over the prior APK retains connections with monitor count one.

Physical Quest/server verification is required because local JVM tests and an
APK build cannot prove Horizon OS panel placement or server-side monitor
enumeration.
