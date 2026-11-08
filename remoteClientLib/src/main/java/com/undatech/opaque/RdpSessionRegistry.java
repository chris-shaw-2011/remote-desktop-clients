package com.undatech.opaque;

import com.freerdp.freerdpcore.application.GlobalApp;
import com.freerdp.freerdpcore.services.LibFreeRDP;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Routes process-wide FreeRDP connection events to their owning session. */
final class RdpSessionRegistry implements LibFreeRDP.EventListener {
    private final Map<Long, LibFreeRDP.EventListener> listeners = new ConcurrentHashMap<>();
    private final Set<Long> activeInstances = ConcurrentHashMap.newKeySet();
    private final SessionReleaser sessionReleaser;
    private boolean installed;

    RdpSessionRegistry() {
        this(RdpSessionRegistry::releaseSession);
    }

    RdpSessionRegistry(SessionReleaser sessionReleaser) {
        this.sessionReleaser = sessionReleaser;
    }

    synchronized void install() {
        if (installed) {
            return;
        }

        LibFreeRDP.setEventListener(this);
        installed = true;
    }

    void register(long instance, LibFreeRDP.EventListener listener) {
        activeInstances.add(instance);
        listeners.put(instance, listener);
    }

    synchronized void replace(long previousInstance, long instance, LibFreeRDP.EventListener listener) {
        if (previousInstance != 0) {
            unregister(previousInstance, listener);
        }
        register(instance, listener);
    }

    synchronized void unregister(long instance, LibFreeRDP.EventListener listener) {
        if (listeners.get(instance) == listener) {
            listeners.remove(instance);
        }
    }

    int size() {
        return listeners.size();
    }

    private static void releaseSession(long instance) {
        Thread cleanupThread = new Thread(
                () -> GlobalApp.freeSession(instance),
                "FreeRdpSessionCleanup-" + instance
        );
        cleanupThread.start();
    }

    private LibFreeRDP.EventListener get(long instance) {
        return listeners.get(instance);
    }

    @Override
    public void OnPreConnect(long instance) {
        LibFreeRDP.EventListener listener = get(instance);
        if (listener != null) {
            listener.OnPreConnect(instance);
        }
    }

    @Override
    public void OnConnectionSuccess(long instance) {
        LibFreeRDP.EventListener listener = get(instance);
        if (listener != null) {
            listener.OnConnectionSuccess(instance);
        }
    }

    @Override
    public void OnConnectionFailure(long instance) {
        LibFreeRDP.EventListener listener = get(instance);
        if (listener != null) {
            listener.OnConnectionFailure(instance);
        }
    }

    @Override
    public void OnDisconnecting(long instance) {
        LibFreeRDP.EventListener listener = get(instance);
        if (listener != null) {
            listener.OnDisconnecting(instance);
        }
    }

    @Override
    public void OnDisconnected(long instance) {
        if (!activeInstances.remove(instance)) {
            return;
        }
        LibFreeRDP.EventListener listener = listeners.remove(instance);
        if (listener != null) {
            listener.OnDisconnected(instance);
        }
        sessionReleaser.release(instance);
    }

    interface SessionReleaser {
        void release(long instance);
    }
}
