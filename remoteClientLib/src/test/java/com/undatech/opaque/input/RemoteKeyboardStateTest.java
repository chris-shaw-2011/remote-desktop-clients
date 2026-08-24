package com.undatech.opaque.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RemoteKeyboardStateTest {
    @Test
    public void physicalShiftIsNotReleasedByMissingOrdinaryKeyMetadata() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.updateHardwareModifier(RemoteKeyboard.SHIFT_MASK, true);
        state.updateRemoteMetaState(RemoteKeyboard.SHIFT_MASK, true);

        assertNull(state.getModifierStateChange(0, RemoteKeyboard.SHIFT_MASK, true));
    }

    @Test
    public void explicitPhysicalShiftUpReleasesRemoteShift() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.updateHardwareModifier(RemoteKeyboard.SHIFT_MASK, true);
        state.updateRemoteMetaState(RemoteKeyboard.SHIFT_MASK, true);

        state.updateHardwareModifier(RemoteKeyboard.SHIFT_MASK, false);

        assertEquals(Boolean.FALSE,
                state.getModifierStateChange(0, RemoteKeyboard.SHIFT_MASK, false));
    }

    @Test
    public void syntheticShiftIsReleasedBackToPhysicalStateAfterInput() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);

        assertEquals(Boolean.TRUE, state.getModifierStateChange(
                RemoteKeyboard.SHIFT_MASK, RemoteKeyboard.SHIFT_MASK, true));
        state.updateRemoteMetaState(RemoteKeyboard.SHIFT_MASK, true);

        assertEquals(Boolean.FALSE,
                state.getModifierStateChange(0, RemoteKeyboard.SHIFT_MASK, false));
    }

    @Test
    public void clearDropsPhysicalAndRemoteState() {
        RemoteKeyboardState state = new RemoteKeyboardState(false);
        state.updateHardwareModifier(RemoteKeyboard.RSHIFT_MASK, true);
        state.updateRemoteMetaState(RemoteKeyboard.RSHIFT_MASK, true);

        state.clear();

        assertNull(state.getModifierStateChange(0, RemoteKeyboard.RSHIFT_MASK, true));
    }
}
