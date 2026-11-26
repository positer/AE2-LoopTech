package com.example.ae2lightoptimizer.integration;

import java.util.function.DoubleUnaryOperator;

/** Integer FE transfer constrained by the AE actually withdrawn. */
final class LoopTickerEnergyTransfer {
    @FunctionalInterface
    interface Source {
        double extract(double ae, boolean simulate);
    }

    @FunctionalInterface
    interface Receiver {
        int insert(int fe, boolean simulate);
    }

    private boolean attempted;
    private long lastTick;

    int push(long gameTick, double aePerFe, Source source, DoubleUnaryOperator refund, Receiver receiver) {
        if ((attempted && lastTick == gameTick) || !Double.isFinite(aePerFe) || aePerFe <= 0) return 0;
        attempted = true;
        lastTick = gameTick;

        double maxAe = Integer.MAX_VALUE * aePerFe; // Native FE insertion uses int; the card has no separate limit.
        int available = wholeFe(source.extract(maxAe, true), aePerFe);
        if (available == 0) return 0;
        int demand = Math.clamp(receiver.insert(available, true), 0, available);
        if (demand == 0) return 0;

        double withdrawn = source.extract(demand * aePerFe, false);
        if (!(withdrawn > 0) || !Double.isFinite(withdrawn)) return 0;
        int funded = Math.min(demand, wholeFe(withdrawn, aePerFe));
        // Never export energy based on simulation alone. Return extraction surplus to the ME grid.
        int accepted = funded == 0 ? 0 : Math.clamp(receiver.insert(funded, false), 0, funded);
        double unused = withdrawn - accepted * aePerFe;
        if (unused > 0) refund.applyAsDouble(unused);
        return accepted;
    }

    private static int wholeFe(double ae, double aePerFe) {
        if (!(ae > 0) || !Double.isFinite(ae)) return 0;
        int result = (int) Math.floor(ae / aePerFe);
        // Use the same rounded multiplication as the actual debit. Division can round
        // either way at an integer boundary, including when AE was debited for exactly MAX FE.
        if (result > 0 && result * aePerFe > ae) return result - 1;
        if (result < Integer.MAX_VALUE && (result + 1) * aePerFe <= ae) return result + 1;
        return result;
    }
}
