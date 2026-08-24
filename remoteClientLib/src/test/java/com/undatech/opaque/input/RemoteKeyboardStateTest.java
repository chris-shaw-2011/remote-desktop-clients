package com.undatech.opaque.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void genericShiftPreservesKnownRightShiftWithoutInventingLeftShift() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.reconcileHardwareShiftState(
                KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_RIGHT_ON);

        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_B, KeyEvent.META_SHIFT_ON);

        assertTrue(state.isHardwareModifierActive(RemoteKeyboard.RSHIFT_MASK));
        assertFalse(state.isHardwareModifierActive(RemoteKeyboard.SHIFT_MASK));
        assertEquals(RemoteKeyboard.RSHIFT_MASK, state.resolveShiftMetaState(
                RemoteKeyboard.SHIFT_MASK, KeyEvent.META_SHIFT_ON, true));
    }

    @Test
    public void genericShiftDoesNotRecreateRightShiftAfterItsRelease() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.reconcileHardwareShiftState(
                KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_RIGHT_ON);
        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_B, 0);

        state.reconcileHardwareShiftState(KeyEvent.KEYCODE_C, KeyEvent.META_SHIFT_ON);

        assertFalse(state.isHardwareModifierActive(RemoteKeyboard.RSHIFT_MASK));
        assertFalse(state.isHardwareModifierActive(RemoteKeyboard.SHIFT_MASK));
        assertEquals(0, state.resolveShiftMetaState(
                RemoteKeyboard.SHIFT_MASK, KeyEvent.META_SHIFT_ON, true));
    }

    @Test
    public void overlappingKeysRetainTheirOwnDownMetaState() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.recordKeyDownMetaState(KeyEvent.KEYCODE_A, RemoteKeyboard.RSHIFT_MASK);
        state.recordKeyDownMetaState(KeyEvent.KEYCODE_B, 0);

        assertEquals(RemoteKeyboard.RSHIFT_MASK,
                state.consumeKeyUpMetaState(KeyEvent.KEYCODE_A, -1));
        assertEquals(0, state.consumeKeyUpMetaState(KeyEvent.KEYCODE_B, -1));
        assertEquals(123, state.consumeKeyUpMetaState(KeyEvent.KEYCODE_C, 123));
    }
}
