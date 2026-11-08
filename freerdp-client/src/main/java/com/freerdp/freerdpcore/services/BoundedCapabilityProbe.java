package com.freerdp.freerdpcore.services;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs a potentially slow native capability check once, outside class initialization. */
final class BoundedCapabilityProbe {
    private final FutureTask<Boolean> result;
    private final AtomicBoolean started = new AtomicBoolean();
    private final long timeoutMillis;

    BoundedCapabilityProbe(Callable<Boolean> probe, long timeoutMillis) {
        result = new FutureTask<>(probe);
        this.timeoutMillis = timeoutMillis;
    }

    boolean get() {
        boolean first = started.compareAndSet(false, true);
        if (first) {
            Thread worker = new Thread(result, "FreeRdpH264Probe");
            worker.setDaemon(true);
            worker.start();
        } else if (!result.isDone()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(result.get(first ? timeoutMillis : 0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            // A timed-out native codec allocation may still finish. Keep its eventual result,
            // and never create another worker for an allocation that cannot be interrupted.
            return false;
        }
    }
}
