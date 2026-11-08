package com.freerdp.freerdpcore.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedCapabilityProbeTest {
    @Test(timeout = 5000) public void staysLazyAndCachesSupportedResult() {
        AtomicInteger calls = new AtomicInteger();
        BoundedCapabilityProbe probe = new BoundedCapabilityProbe(() -> {
            calls.incrementAndGet();
            return true;
        }, 1000);
        assertEquals(0, calls.get());
        assertTrue(probe.get());
        assertTrue(probe.get());
        assertEquals(1, calls.get());
    }

    @Test(timeout = 5000) public void hungCheckIsBoundedAndNeverSpawnsAnotherWorker() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        BoundedCapabilityProbe probe = new BoundedCapabilityProbe(() -> {
            calls.incrementAndGet();
            entered.countDown();
            release.await();
            return true;
        }, 5);
        try {
            assertFalse(probe.get());
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            for (int i = 0; i < 5; i++) assertFalse(probe.get());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!probe.get() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(probe.get());
        assertEquals(1, calls.get());
    }

    @Test(timeout = 5000) public void failedCheckFallsBackWithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();
        BoundedCapabilityProbe probe = new BoundedCapabilityProbe(() -> {
            calls.incrementAndGet();
            throw new IOException("Synthetic codec failure");
        }, 1000);
        assertFalse(probe.get());
        assertFalse(probe.get());
        assertEquals(1, calls.get());
    }

    @Test(timeout = 5000) public void interruptionIsPreserved() {
        CountDownLatch release = new CountDownLatch(1);
        BoundedCapabilityProbe probe = new BoundedCapabilityProbe(() -> {
            release.await();
            return true;
        }, 1000);
        try {
            Thread.currentThread().interrupt();
            assertFalse(probe.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            release.countDown();
        }
    }
}
