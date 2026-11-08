package com.freerdp.freerdpcore.application;

import android.content.Context;
import android.net.Uri;

import com.freerdp.freerdpcore.domain.BookmarkBase;
import com.freerdp.freerdpcore.services.LibFreeRDP;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Session ownership for the embedded client, independent of upstream's standalone UI. */
public final class GlobalApp {
    private static final Map<Long, SessionState> sessions = new ConcurrentHashMap<>();

    private GlobalApp() {}

    public static SessionState createSession(BookmarkBase bookmark, Context context) {
        return register(new SessionState(LibFreeRDP.newInstance(context), bookmark));
    }

    public static SessionState createSession(Uri uri, Context context) {
        return register(new SessionState(LibFreeRDP.newInstance(context), uri));
    }

    private static SessionState register(SessionState session) {
        if (session.getInstance() == 0) {
            throw new IllegalStateException("Unable to allocate a FreeRDP session");
        }
        sessions.put(session.getInstance(), session);
        return session;
    }

    public static SessionState getSession(long instance) {
        return sessions.get(instance);
    }

    public static void freeSession(long instance) {
        if (sessions.remove(instance) != null) {
            LibFreeRDP.freeInstance(instance);
        }
    }
}
