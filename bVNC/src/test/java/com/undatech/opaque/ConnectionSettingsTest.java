package com.undatech.opaque;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionSettingsTest {
    @Test
    public void fontSmoothingDefaultsToEnabled() {
        assertTrue(new ConnectionSettings("test").getFontSmoothing());
    }

    @Test
    public void fontSmoothingCanBeDisabled() {
        ConnectionSettings connection = new ConnectionSettings("test");

        connection.setFontSmoothing(false);

        assertFalse(connection.getFontSmoothing());
    }
}
