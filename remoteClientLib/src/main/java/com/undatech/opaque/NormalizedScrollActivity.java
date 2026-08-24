package com.undatech.opaque;

import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

/** Normalizes malformed mouse-wheel axes before views or activities consume them. */
public abstract class NormalizedScrollActivity extends AppCompatActivity {
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

    static float normalizeScrollAxis(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static MotionEvent normalizeScrollEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) {
            return event;
        }

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
            float normalizedVertical = normalizeScrollAxis(vertical);
            float normalizedHorizontal = normalizeScrollAxis(horizontal);
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
