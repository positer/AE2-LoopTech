package com.example.ae2lightoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoopTickerCascadeTest {
    @Test void directContributionsAddAndChainedActivationsMultiply() {
        var cascade = new LoopTickerCascade<Object>(4096);
        Object first = new Object(), second = new Object(), downstream = new Object();
        var output = new AtomicInteger();
        for (int amount : new int[] {2, 3}) {
            cascade.run(amount == 2 ? first : second, amount, () -> {
                cascade.run(downstream, 4, () -> { output.incrementAndGet(); return true; });
                return true;
            });
        }
        assertEquals(20, output.get());
    }

    @Test void cutsOnlyTheBackEdgeNotTheValidSibling() {
        var cascade = new LoopTickerCascade<Object>(4096);
        Object a = new Object(), b = new Object(), sibling = new Object();
        var output = new AtomicInteger();
        cascade.run(a, 2, () -> {
            cascade.run(b, 2, () -> {
                cascade.run(a, 64, () -> { throw new AssertionError("cycle re-entered"); });
                cascade.run(sibling, 1, () -> { output.incrementAndGet(); return true; });
                return true;
            });
            return true;
        });
        assertEquals(4, output.get());
    }

    @Test void extremeCascadeIsBoundedAndLeavesNoStateForTheNextRoot() {
        var cascade = new LoopTickerCascade<Object>(4096);
        var calls = new AtomicInteger();
        class Deep {
            void launch() {
                cascade.run(new Object(), 1, () -> { calls.incrementAndGet(); launch(); return true; });
            }
        }
        new Deep().launch();
        assertEquals(4096, calls.get());
        cascade.run(new Object(), 2, () -> { calls.incrementAndGet(); return true; });
        assertEquals(4098, calls.get());
    }
}
