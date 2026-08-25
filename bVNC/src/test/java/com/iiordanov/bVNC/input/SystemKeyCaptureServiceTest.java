package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class SystemKeyCaptureServiceTest {
    @Test
    public void capturesBothMetaKeysAndMetaCombinations() {
        assertTrue(SystemKeyCaptureService.isMetaKeyEvent(KeyEvent.KEYCODE_META_LEFT, 0));
        assertTrue(SystemKeyCaptureService.isMetaKeyEvent(KeyEvent.KEYCODE_META_RIGHT, 0));
        assertTrue(SystemKeyCaptureService.isMetaKeyEvent(
                KeyEvent.KEYCODE_R, KeyEvent.META_META_LEFT_ON));
    }

    @Test
    public void leavesOtherKeysAlone() {
        assertFalse(SystemKeyCaptureService.isMetaKeyEvent(KeyEvent.KEYCODE_R, 0));
    }
}
