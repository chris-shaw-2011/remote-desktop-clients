package com.iiordanov.bVNC.input;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

/**
 * Captures Meta key combinations before Android's system-key policy handles them.
 */
public class SystemKeyCaptureService extends AccessibilityService {
    private static WeakReference<KeyEventListener> listener = new WeakReference<>(null);

    public interface KeyEventListener {
        boolean onCapturedKeyEvent(KeyEvent event);
    }

    public static void setKeyEventListener(KeyEventListener newListener) {
        listener = new WeakReference<>(newListener);
    }

    public static void clearKeyEventListener(KeyEventListener oldListener) {
        if (listener.get() == oldListener) {
            listener.clear();
        }
    }

    static boolean isMetaKeyEvent(int keyCode, int metaState) {
        return keyCode == KeyEvent.KEYCODE_META_LEFT
                || keyCode == KeyEvent.KEYCODE_META_RIGHT
                || (metaState & KeyEvent.META_META_MASK) != 0;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        KeyEventListener currentListener = listener.get();
        return currentListener != null
                && isMetaKeyEvent(event.getKeyCode(), event.getMetaState())
                && currentListener.onCapturedKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service only filters key events.
    }

    @Override
    public void onInterrupt() {
    }
}
