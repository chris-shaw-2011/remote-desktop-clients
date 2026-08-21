package com.undatech.opaque;

import static org.junit.Assert.assertEquals;

import com.freerdp.freerdpcore.services.LibFreeRDP;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RdpSessionRegistryTest {
    @Test
    public void routesEventsToTheirOwningSessions() {
        List<Long> released = new ArrayList<>();
        RdpSessionRegistry registry = new RdpSessionRegistry(released::add);
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();

        registry.register(11, first);
        registry.register(22, second);
        registry.OnPreConnect(11);
        registry.OnConnectionSuccess(22);
        registry.OnConnectionFailure(11);
        registry.OnDisconnecting(22);

        assertEquals(List.of("pre:11", "failure:11"), first.events);
        assertEquals(List.of("success:22", "disconnecting:22"), second.events);
        assertEquals(2, registry.size());
        assertEquals(List.of(), released);
    }

    @Test
    public void disconnectingOneSessionLeavesTheOtherRegistered() {
        List<Long> released = new ArrayList<>();
        RdpSessionRegistry registry = new RdpSessionRegistry(released::add);
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();

        registry.register(11, first);
        registry.register(22, second);
        registry.OnDisconnected(11);
        registry.OnConnectionSuccess(22);

        assertEquals(List.of("disconnected:11"), first.events);
        assertEquals(List.of("success:22"), second.events);
        assertEquals(1, registry.size());
        assertEquals(List.of(11L), released);
    }

    @Test
    public void replacingAHandleDropsCallbacksFromTheSupersededSession() {
        List<Long> released = new ArrayList<>();
        RdpSessionRegistry registry = new RdpSessionRegistry(released::add);
        RecordingListener listener = new RecordingListener();

        registry.register(11, listener);
        registry.replace(11, 33, listener);
        registry.OnConnectionFailure(11);
        registry.OnDisconnected(11);
        registry.OnDisconnected(11);
        registry.OnConnectionSuccess(33);

        assertEquals(List.of("success:33"), listener.events);
        assertEquals(1, registry.size());
        assertEquals(List.of(11L), released);
    }

    private static class RecordingListener implements LibFreeRDP.EventListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void OnPreConnect(long instance) {
            events.add("pre:" + instance);
        }

        @Override
        public void OnConnectionSuccess(long instance) {
            events.add("success:" + instance);
        }

        @Override
        public void OnConnectionFailure(long instance) {
            events.add("failure:" + instance);
        }

        @Override
        public void OnDisconnecting(long instance) {
            events.add("disconnecting:" + instance);
        }

        @Override
        public void OnDisconnected(long instance) {
            events.add("disconnected:" + instance);
        }
    }
}
