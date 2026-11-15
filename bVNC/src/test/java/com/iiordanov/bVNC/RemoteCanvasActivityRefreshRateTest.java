package com.iiordanov.bVNC;

import android.view.Display;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RemoteCanvasActivityRefreshRateTest {
    @Test
    public void selectsHighestRateAtCurrentPhysicalResolution() {
        Display.Mode[] modes = {
                mode(2064, 2208, 72f),
                mode(2064, 2208, 90f),
                mode(2064, 2208, 120f),
                mode(1832, 1920, 144f)
        };

        assertEquals(120f, RemoteCanvasActivity.highestRefreshRateForResolution(
                modes, 2064, 2208), 0f);
    }

    @Test
    public void returnsNoRateForNullOrUnmatchedModes() {
        assertEquals(0f, RemoteCanvasActivity.highestRefreshRateForResolution(null,
                2064, 2208), 0f);
        assertEquals(0f, RemoteCanvasActivity.highestRefreshRateForResolution(
                new Display.Mode[]{mode(2064, 2208, 72f)},
                1832, 1920), 0f);
    }

    @Test
    public void handlesSingleMatchingMode() {
        assertEquals(72f, RemoteCanvasActivity.highestRefreshRateForResolution(
                new Display.Mode[]{mode(1280, 720, 72f)},
                1280, 720), 0f);
    }

    private static Display.Mode mode(int width, int height, float refreshRate) {
        return ReflectionHelpers.callConstructor(Display.Mode.class,
                ClassParameter.from(int.class, 1),
                ClassParameter.from(int.class, width),
                ClassParameter.from(int.class, height),
                ClassParameter.from(float.class, refreshRate));
    }
}
