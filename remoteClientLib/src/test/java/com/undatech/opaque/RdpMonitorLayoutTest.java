package com.undatech.opaque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RdpMonitorLayoutTest {
    @Test
    public void clampsMonitorCountAndDesktopWidth() {
        assertEquals(1, RdpMonitorLayout.clampCount(0));
        assertEquals(16, RdpMonitorLayout.clampCount(17));
        assertEquals(16383, RdpMonitorLayout.maxMonitorWidth(2));
    }

    @Test
    public void intersectsUpdateAcrossMonitorBoundary() {
        RdpMonitorLayout.Region first = RdpMonitorLayout.intersect(900, 10, 300, 40, 0, 1000);
        RdpMonitorLayout.Region second = RdpMonitorLayout.intersect(900, 10, 300, 40, 1, 1000);

        assertEquals(900, first.sourceX);
        assertEquals(900, first.destinationX);
        assertEquals(100, first.width);
        assertEquals(1000, second.sourceX);
        assertEquals(0, second.destinationX);
        assertEquals(200, second.width);
        assertNull(RdpMonitorLayout.intersect(900, 10, 300, 40, 2, 1000));
    }

    @Test
    public void offsetsPointerCoordinatesByMonitorOrigin() {
        assertEquals(2123, RdpMonitorLayout.monitorOriginX(2, 1000) + 123);
    }
}
