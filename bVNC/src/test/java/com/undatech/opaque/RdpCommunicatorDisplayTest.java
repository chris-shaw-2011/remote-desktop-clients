package com.undatech.opaque;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.os.Handler;

import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.domain.BookmarkBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.util.HashMap;

@RunWith(RobolectricTestRunner.class)
public class RdpCommunicatorDisplayTest {

    @Test
    public void settingsChangedAcceptsServerSizeWithoutChangingSavedConnectionOrReconnecting() {
        RdpCommunicator communicator = mock(RdpCommunicator.class, CALLS_REAL_METHODS);
        Connection connection = mock(Connection.class);
        SessionState session = mock(SessionState.class);
        BookmarkBase bookmark = new BookmarkBase();
        when(session.getBookmark()).thenReturn(bookmark);
        ReflectionHelpers.setField(communicator, "connection", connection);
        ReflectionHelpers.setField(communicator, "session", session);
        ReflectionHelpers.setField(communicator, "monitorCount", 1);
        ReflectionHelpers.setField(communicator, "monitorTargets", new HashMap<>());

        Handler handler = mock(Handler.class);
        Viewable viewable = mock(Viewable.class);
        communicator.attachMonitor(0, handler, viewable);
        Object target = ((HashMap<?, ?>) ReflectionHelpers.getField(communicator, "monitorTargets")).get(0);
        ReflectionHelpers.setField(target, "ready", true);

        communicator.OnSettingsChanged(1920, 1080, 32);

        BookmarkBase.ScreenSettings settings = bookmark.getActiveScreenSettings();
        assertEquals(1920, settings.getWidth());
        assertEquals(1080, settings.getHeight());
        assertEquals(32, settings.getColors());
        verify(viewable).reallocateDrawable(1920, 1080);
        verify(handler, never()).sendEmptyMessage(RemoteClientLibConstants.REINIT_SESSION);
        verifyNoInteractions(connection);
    }
}
