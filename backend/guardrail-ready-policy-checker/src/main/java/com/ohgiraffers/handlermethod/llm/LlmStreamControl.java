package com.ohgiraffers.handlermethod.llm;

import java.util.ArrayList;
import java.util.List;

/** Request-local transport cancellation and last verified provider usage. */
public final class LlmStreamControl {
    private boolean cancelled;
    private boolean dispatched;
    private StreamingLlmClient.Usage usage;
    private final List<Runnable> cancellations = new ArrayList<>();

    public synchronized boolean dispatch(Runnable start) {
        if (cancelled) return false;
        dispatched = true;
        start.run();
        return true;
    }

    public void onCancel(Runnable cancellation) {
        synchronized (this) {
            if (!cancelled) { cancellations.add(cancellation); return; }
        }
        runSafely(cancellation);
    }

    public synchronized void reportUsage(StreamingLlmClient.Usage value) {
        if (!cancelled && value != null && value.usageAvailable()) usage = value;
    }

    public synchronized Snapshot snapshot() { return new Snapshot(dispatched, usage); }
    public synchronized boolean cancelled() { return cancelled; }

    public Snapshot cancel() {
        List<Runnable> callbacks;
        Snapshot result;
        synchronized (this) {
            result = snapshot();
            if (cancelled) return result;
            cancelled = true;
            callbacks = List.copyOf(cancellations);
            cancellations.clear();
        }
        callbacks.forEach(LlmStreamControl::runSafely);
        return result;
    }

    private static void runSafely(Runnable callback) {
        try { callback.run(); } catch (RuntimeException ignored) { /* Continue closing other resources. */ }
    }

    public record Snapshot(boolean dispatched, StreamingLlmClient.Usage usage) { }
}
