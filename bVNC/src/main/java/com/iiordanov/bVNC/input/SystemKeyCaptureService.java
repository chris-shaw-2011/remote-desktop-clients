package com.iiordanov.bVNC.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

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

    public static boolean isMetaKeyEvent(int keyCode, int metaState) {
        return keyCode == KeyEvent.KEYCODE_META_LEFT
                || keyCode == KeyEvent.KEYCODE_META_RIGHT
                || (metaState & KeyEvent.META_META_MASK) != 0;
    }

    static boolean isTargetPackage(CharSequence targetPackage, CharSequence focusedPackage) {
        return targetPackage != null && targetPackage.equals(focusedPackage);
    }

    @Override
    protected void onServiceConnected() {
        updateKeyFiltering();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        KeyEventListener currentListener = listener.get();
        return isRemoteCanvasFocused()
                && currentListener != null
                && isMetaKeyEvent(event.getKeyCode(), event.getMetaState())
                && currentListener.onCapturedKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        updateKeyFiltering();
    }

    private void updateKeyFiltering() {
        AccessibilityServiceInfo info = getServiceInfo();
        int oldFlags = info.flags;
        if (isRemoteCanvasFocused()) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        if (info.flags != oldFlags) {
            setServiceInfo(info);
        }
    }

    private boolean isRemoteCanvasFocused() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (!window.isActive() && !window.isFocused()) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && isTargetPackage(getPackageName(), root.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
