# Mouse-wheel scrolling on Quest

## Symptom and cause

With a Bluetooth Logitech MX Master 3 connected to Quest 3, one wheel movement
could scroll more than a page in both aRDP settings and the remote Windows
desktop. This showed that the problem occurred before RDP translation.

Android defines mouse `AXIS_VSCROLL` and `AXIS_HSCROLL` values as normalized to
the `-1.0` to `1.0` range. Quest can deliver a much larger magnitude for this
mouse. Standard Android widgets multiply that value by their configured pixel
scroll factor, causing the excessive local movement. The remote-canvas input
handler interpreted the magnitude as a wheel-event count and could send up to
seven 120-unit RDP wheel ticks for one Android event.

## Fix and decisions

`NormalizedScrollActivity` intercepts generic mouse motion before a view or
activity consumes it. It copies mouse-source `ACTION_SCROLL` events, clamps
their vertical and horizontal wheel axes to `-1.0` or `1.0`, and applies the
app-wide Mouse Wheel Scroll Speed percentage while preserving direction,
coordinates, buttons, modifiers, device identity, and source. The temporary
copy is recycled after dispatch.

The base activity is used by the connection grid, connection editor/default
settings, global settings, and remote canvas, so local and RDP scrolling follow
the same rule. Valid fractional high-resolution wheel values remain unchanged.
Touch gestures and non-mouse scroll sources are not modified.

The percentage is available in Global Settings, ranges from 5% to 200%, and
defaults to 100% (the normalized behavior from the original fix). Remote input
accumulates fractional wheel movement so values below 100% skip wheel ticks at
a steady rate instead of rounding every event down to zero.

## Validation

Unit tests cover positive and negative clamping, preservation of in-range
fractional values, and multiplier application. The `remoteClientLib` unit suite
and affected bVNC Java/Kotlin compilation pass.

On Quest 3, verify the MX Master in both ratchet and free-spin modes in the
connection/default settings and a long remote Windows document. Each detent
should produce a normal scroll step, free-spin should remain responsive, and
touch scrolling should be unchanged.
