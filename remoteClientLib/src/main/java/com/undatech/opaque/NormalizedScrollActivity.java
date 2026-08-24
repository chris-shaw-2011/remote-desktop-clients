package com.undatech.opaque;

import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

/** Normalizes malformed mouse-wheel axes before views or activities consume them. */
public abstract class NormalizedScrollActivity extends AppCompatActivity {
    private static final String GENERAL_SETTINGS = "generalSettings";
    private static final String MOUSE_WHEEL_SCROLL_MULTIPLIER = "mouseWheelScrollMultiplier";
    private static final int DEFAULT_MOUSE_WHEEL_SCROLL_MULTIPLIER = 100;

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        MotionEvent normalizedEvent = normalizeScrollEvent(event);
        try {
            return super.dispatchGenericMotionEvent(normalizedEvent);
        } finally {
            if (normalizedEvent != event) {
                normalizedEvent.recycle();
            }
        }
    }

    static float normalizeScrollAxis(float value, float multiplier) {
        return Math.max(-1f, Math.min(1f, value)) * multiplier;
    }

    private MotionEvent normalizeScrollEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL ||
                !event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return event;
        }

        float multiplier = getSharedPreferences(GENERAL_SETTINGS, MODE_PRIVATE)
                .getInt(MOUSE_WHEEL_SCROLL_MULTIPLIER, DEFAULT_MOUSE_WHEEL_SCROLL_MULTIPLIER) / 100f;

        int pointerCount = event.getPointerCount();
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[pointerCount];
        boolean changed = false;

        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            event.getPointerProperties(i, properties[i]);
            coordinates[i] = new MotionEvent.PointerCoords();
            event.getPointerCoords(i, coordinates[i]);

            float vertical = coordinates[i].getAxisValue(MotionEvent.AXIS_VSCROLL);
            float horizontal = coordinates[i].getAxisValue(MotionEvent.AXIS_HSCROLL);
            float normalizedVertical = normalizeScrollAxis(vertical, multiplier);
            float normalizedHorizontal = normalizeScrollAxis(horizontal, multiplier);
            coordinates[i].setAxisValue(MotionEvent.AXIS_VSCROLL, normalizedVertical);
            coordinates[i].setAxisValue(MotionEvent.AXIS_HSCROLL, normalizedHorizontal);
            changed |= vertical != normalizedVertical || horizontal != normalizedHorizontal;
        }

        if (!changed) {
            return event;
        }

        return MotionEvent.obtain(
                event.getDownTime(), event.getEventTime(), event.getAction(), pointerCount,
                properties, coordinates, event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(), event.getDeviceId(),
                event.getEdgeFlags(), event.getSource(), event.getFlags());
    }
}
