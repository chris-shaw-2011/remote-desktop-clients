package com.iiordanov.questadb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArdpAccessibilityTest {
    private static final String SERVICE = "com.iiordanov.aRDP/service";

    @Test
    public void addsServiceToEmptySetting() {
        assertEquals(SERVICE, ArdpAccessibility.addEnabledService(null, SERVICE));
    }

    @Test
    public void preservesOtherServices() {
        assertEquals("other/one:other/two:" + SERVICE,
                ArdpAccessibility.addEnabledService("other/one:other/two", SERVICE));
    }

    @Test
    public void doesNotDuplicateExistingService() {
        assertEquals("other/one:" + SERVICE,
                ArdpAccessibility.addEnabledService("other/one:" + SERVICE, SERVICE));
    }
}
