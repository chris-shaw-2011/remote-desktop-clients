package com.iiordanov.bVNC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ConnectionQualityDefaultsTest {
    @Test
    public void newConnectionUsesHighQualityRdpDefaults() {
        Context context = RuntimeEnvironment.getApplication();

        ConnectionBean connection = new ConnectionBean(context);

        assertEquals(32, connection.getRdpColor());
        assertTrue(connection.getFontSmoothing());
    }

    @Test
    public void qualityDefaultsRemainUserConfigurable() {
        ConnectionBean connection = new ConnectionBean(RuntimeEnvironment.getApplication());

        connection.setRdpColor(16);
        connection.setFontSmoothing(false);

        assertEquals(16, connection.getRdpColor());
        assertFalse(connection.getFontSmoothing());
    }
}
