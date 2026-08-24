/**
 * Copyright (C) 2021- Iordan Iordanov
 * <p>
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package com.undatech.opaque.input;

import android.view.KeyEvent;

import com.undatech.opaque.util.GeneralUtils;

public class RemoteKeyboardState {
    private static final String TAG = "RemoteKeyboardState";
    private int remoteKeyboardMetaState = 0;
    private int hardwareMetaState = 0;
    private boolean debugLogging = false;

    public RemoteKeyboardState(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public void detectHardwareMetaState(KeyEvent event) {
        int modifier = getHardwareModifier(event.getKeyCode(), event.getScanCode());
        if (modifier == 0)
            return;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_MULTIPLE;
        updateHardwareModifier(modifier, down);
        GeneralUtils.debugLog(this.debugLogging, TAG,
                "detected hardware modifier " + modifier + ", down: " + down);
    }

    private int getHardwareModifier(int keyCode, int scanCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CTRL_LEFT: return RemoteKeyboard.CTRL_MASK;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return RemoteKeyboard.RCTRL_MASK;
            case KeyEvent.KEYCODE_ALT_LEFT: return RemoteKeyboard.ALT_MASK;
            case KeyEvent.KEYCODE_ALT_RIGHT: return RemoteKeyboard.RALT_MASK;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return RemoteKeyboard.SHIFT_MASK;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return RemoteKeyboard.RSHIFT_MASK;
            case KeyEvent.KEYCODE_META_LEFT: return RemoteKeyboard.SUPER_MASK;
            case KeyEvent.KEYCODE_META_RIGHT: return RemoteKeyboard.RSUPER_MASK;
            case KeyEvent.KEYCODE_DPAD_CENTER: return RemoteKeyboard.CTRL_MASK;
        }
        switch (scanCode) {
            case RemoteKeyboard.SCAN_LEFTCTRL: return RemoteKeyboard.CTRL_MASK;
            case RemoteKeyboard.SCAN_RIGHTCTRL: return RemoteKeyboard.RCTRL_MASK;
            case RemoteKeyboard.SCAN_LEFTALT: return RemoteKeyboard.ALT_MASK;
            case RemoteKeyboard.SCAN_RIGHTALT: return RemoteKeyboard.RALT_MASK;
            case RemoteKeyboard.SCAN_LEFTSHIFT: return RemoteKeyboard.SHIFT_MASK;
            case RemoteKeyboard.SCAN_RIGHTSHIFT: return RemoteKeyboard.RSHIFT_MASK;
            case RemoteKeyboard.SCAN_LEFTSUPER: return RemoteKeyboard.SUPER_MASK;
            case RemoteKeyboard.SCAN_RIGHTSUPER: return RemoteKeyboard.RSUPER_MASK;
            default: return 0;
        }
    }

    void updateHardwareModifier(int modifier, boolean down) {
        if (down)
            hardwareMetaState |= modifier;
        else
            hardwareMetaState &= ~modifier;
    }

    public boolean isHardwareModifierActive(int modifier) {
        return (hardwareMetaState & modifier) != 0;
    }

    public boolean isRemoteModifierActive(int modifier) {
        return (remoteKeyboardMetaState & modifier) != 0;
    }

    public Boolean getModifierStateChange(int softwareMetaState, int modifier, boolean beforeInput) {
        int targetMetaState = hardwareMetaState | (beforeInput ? softwareMetaState : 0);
        boolean targetState = (targetMetaState & modifier) != 0;
        return targetState == isRemoteModifierActive(modifier) ? null : targetState;
    }

    public boolean shouldSendModifier(int softwareMetaState,
                                      int modifier, boolean keyDown) {
        boolean shouldSend = false;
        boolean wasSentAsHardwareKeyAlready = (hardwareMetaState & modifier) != 0;
        boolean softwareMetaStateContainsModifier = (softwareMetaState & modifier) != 0;
        boolean isKeyDownAndRemoteUp = keyDown && (remoteKeyboardMetaState & modifier) == 0;
        boolean isKeyUpAndRemoteDown = !keyDown && (remoteKeyboardMetaState & modifier) != 0;
        boolean hasChangedState = softwareMetaStateContainsModifier && (isKeyDownAndRemoteUp || isKeyUpAndRemoteDown);

        // Send simulated modifier only if it wasn't sent as a hardware key already
        // and if it wasn't sent already to prevent multiple down events for the same modifier
        if (hasChangedState && !wasSentAsHardwareKeyAlready) {
            GeneralUtils.debugLog(this.debugLogging, TAG, "shouldSendModifier, shouldSend: true" +
                    ", wasSentAsHardwareKeyAlready: " + wasSentAsHardwareKeyAlready +
                    ", softwareMetaStateContainsModifier: " + softwareMetaStateContainsModifier +
                    ", hasChangedState: " + hasChangedState);
            shouldSend = true;
        }

        return shouldSend;
    }

    public void updateRemoteMetaState(int modifier, boolean down) {
        if (down) {
            remoteKeyboardMetaState |= modifier;
        } else {
            remoteKeyboardMetaState &= ~modifier;
        }
    }

    public void clear() {
        remoteKeyboardMetaState = 0;
        hardwareMetaState = 0;
    }
}
