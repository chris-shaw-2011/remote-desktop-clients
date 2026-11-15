package com.iiordanov.bVNC;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import com.undatech.opaque.Connection;
import com.undatech.opaque.RemoteClientLibConstants;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class RemoteCanvasTest {

    @Test
    public void rdpDrawableUsesArgb8888() throws Exception {
        TestRemoteCanvas canvas = new TestRemoteCanvas(Robolectric.buildActivity(Activity.class).setup().get());
        canvas.isRdp = true;

        Method allocate = RemoteCanvas.class.getDeclaredMethod("reallocateMyDrawable", int.class, int.class);
        allocate.setAccessible(true);
        allocate.invoke(canvas, 4, 4);

        assertEquals(Bitmap.Config.ARGB_8888, canvas.getBitmap().getConfig());
    }

    @Test
    public void redrawOverloadsScheduleVsyncInvalidation() {
        TestRemoteCanvas canvas = new TestRemoteCanvas(Robolectric.buildActivity(Activity.class).setup().get());

        canvas.reDraw(1, 2, 3, 4);
        canvas.reDraw(1f, 2f, 3f, 4f);

        assertEquals(2, canvas.vsyncInvalidations);
    }

    @Test
    public void nativeDimensionsFollowRequestedOrientationAndRoundOddPixelsDown() {
        TestRemoteCanvas canvas = new TestRemoteCanvas(Robolectric.buildActivity(Activity.class).setup().get());
        Connection connection = mock(Connection.class);
        canvas.connection = connection;
        when(connection.getRdpWidth()).thenReturn(0);
        when(connection.getRdpHeight()).thenReturn(0);

        when(connection.getRdpResType()).thenReturn(RemoteClientLibConstants.RDP_GEOM_SELECT_NATIVE_LANDSCAPE);
        assertEquals(2208, canvas.getRemoteWidth(2063, 2208));
        assertEquals(2062, canvas.getRemoteHeight(2063, 2208));

        when(connection.getRdpResType()).thenReturn(RemoteClientLibConstants.RDP_GEOM_SELECT_NATIVE_PORTRAIT);
        assertEquals(2062, canvas.getRemoteWidth(2208, 2063));
        assertEquals(2208, canvas.getRemoteHeight(2208, 2063));
    }

    @Test
    public void customDimensionsIgnoreCanvasRotationAndPreserveEvenSavedSize() {
        TestRemoteCanvas canvas = new TestRemoteCanvas(Robolectric.buildActivity(Activity.class).setup().get());
        Connection connection = mock(Connection.class);
        canvas.connection = connection;
        when(connection.getRdpResType()).thenReturn(RemoteClientLibConstants.RDP_GEOM_SELECT_CUSTOM);
        when(connection.getRdpWidth()).thenReturn(1600);
        when(connection.getRdpHeight()).thenReturn(900);

        assertEquals(1600, canvas.getRemoteWidth(2208, 2064));
        assertEquals(900, canvas.getRemoteHeight(2208, 2064));
    }

    private static class TestRemoteCanvas extends RemoteCanvas {
        int vsyncInvalidations;

        TestRemoteCanvas(Activity activity) {
            super(activity, null);
        }

        @Override
        public void postInvalidateOnAnimation() {
            vsyncInvalidations++;
        }
    }
}
