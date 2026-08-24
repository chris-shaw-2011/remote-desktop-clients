package com.undatech.opaque.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RdpKeyboardMapperTest {
    @Test
    public void capsLockChangesOnlyOncePerAndroidLockState() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(false, false);

        assertTrue(mapper.updateCapsLockState(true));
        assertFalse(mapper.updateCapsLockState(true));
        assertTrue(mapper.updateCapsLockState(false));
        assertFalse(mapper.updateCapsLockState(false));
    }
}
