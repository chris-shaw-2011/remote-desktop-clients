package com.undatech.opaque;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NormalizedScrollActivityTest {
    @Test
    public void clampsOutOfRangeMouseWheelAxes() {
        assertEquals(1f, NormalizedScrollActivity.normalizeScrollAxis(15f), 0f);
        assertEquals(-1f, NormalizedScrollActivity.normalizeScrollAxis(-15f), 0f);
    }

    @Test
    public void preservesNormalizedAndHighResolutionAxes() {
        assertEquals(1f, NormalizedScrollActivity.normalizeScrollAxis(1f), 0f);
        assertEquals(-1f, NormalizedScrollActivity.normalizeScrollAxis(-1f), 0f);
        assertEquals(0.25f, NormalizedScrollActivity.normalizeScrollAxis(0.25f), 0f);
        assertEquals(-0.25f, NormalizedScrollActivity.normalizeScrollAxis(-0.25f), 0f);
    }
}
