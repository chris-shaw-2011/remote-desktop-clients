package com.undatech.opaque.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.KeyEvent;

import org.junit.Test;

public class RemoteKeyboardStateTest {
    @Test
    public void laterKeyReleasesShiftWhenItsKeyUpWasMissed() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_LEFT_ON);
        state.updateRemoteMetaState(RemoteKeyboard.SHIFT_MASK, true);

        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_B, 0);

        assertEquals(Boolean.FALSE, state.getModifierStateChange(
                0, RemoteKeyboard.SHIFT_MASK, true));
    }

    @Test
    public void heldPhysicalShiftRemainsPressedAfterAKey() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_LEFT_ON);
        state.updateRemoteMetaState(RemoteKeyboard.SHIFT_MASK, true);

        assertNull(state.getModifierStateChange(
                KeyEvent.META_SHIFT_LEFT_ON, RemoteKeyboard.SHIFT_MASK, false));
    }
}
