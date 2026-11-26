package com.example.ae2lightoptimizer.integration;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Runs nested accelerations without using the Java call stack. */
final class LoopTickerCascade<T> {
    private final int workLimit;
    private final ThreadLocal<Run<T>> current = new ThreadLocal<>();

    LoopTickerCascade(int workLimit) {
        this.workLimit = workLimit;
    }

    void run(T source, int times, BooleanSupplier step) {
        if (times <= 0) return;
        var active = current.get();
        if (active != null) {
            if (!active.path.contains(source)) active.frames.push(new Frame<>(source, times, step));
            return;
        }

        active = new Run<>();
        current.set(active);
        active.frames.push(new Frame<>(source, times, step));
        try {
            int remainingWork = workLimit;
            while (!active.frames.isEmpty() && remainingWork > 0) {
                var frame = active.frames.peek();
                if (!frame.entered) {
                    if (!active.path.add(frame.source)) {
                        active.frames.pop();
                        continue;
                    }
                    frame.entered = true;
                }
                if (frame.remaining == 0) {
                    active.path.remove(frame.source);
                    active.frames.pop();
                    continue;
                }
                frame.remaining--;
                remainingWork--;
                if (!frame.step.getAsBoolean()) frame.remaining = 0;
            }
        } finally {
            current.remove();
        }
    }

    private static final class Run<T> {
        final ArrayDeque<Frame<T>> frames = new ArrayDeque<>();
        final Set<T> path = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static final class Frame<T> {
        final T source;
        final BooleanSupplier step;
        int remaining;
        boolean entered;

        Frame(T source, int remaining, BooleanSupplier step) {
            this.source = source;
            this.remaining = remaining;
            this.step = step;
        }
    }
}
