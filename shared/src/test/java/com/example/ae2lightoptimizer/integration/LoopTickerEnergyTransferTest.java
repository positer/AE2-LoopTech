package com.example.ae2lightoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoopTickerEnergyTransferTest {
    private static final double AE_PER_FE = 0.5;

    @Test
    void skipsSubUnitPowerAndFullReceiverWithoutRealExtraction() {
        var transfer = new LoopTickerEnergyTransfer();
        int[] realExtractions = {0};
        LoopTickerEnergyTransfer.Source source = (amount, simulate) -> {
            if (!simulate) realExtractions[0]++;
            return 0.49;
        };
        assertEquals(0, transfer.push(1, AE_PER_FE, source, amount -> 0, (amount, simulate) -> amount));
        assertEquals(0, transfer.push(2, AE_PER_FE, (amount, simulate) -> {
            if (!simulate) realExtractions[0]++;
            return 1;
        }, amount -> 0, (amount, simulate) -> 0));
        assertEquals(0, realExtractions[0]);
    }

    @Test
    void partialAcceptanceReturnsSurplusAndSameTickCannotTransferTwice() {
        var transfer = new LoopTickerEnergyTransfer();
        double[] ae = {20};
        int[] accepted = {0};
        LoopTickerEnergyTransfer.Source source = (amount, simulate) -> {
            double drawn = Math.min(amount, ae[0]);
            if (!simulate) ae[0] -= drawn;
            return drawn;
        };
        java.util.function.DoubleUnaryOperator refund = amount -> {
            ae[0] += amount;
            return 0;
        };
        LoopTickerEnergyTransfer.Receiver receiver = (amount, simulate) -> {
            if (simulate) return 23;
            assertEquals(23, amount);
            accepted[0] += 7;
            return 7;
        };
        assertEquals(7, transfer.push(1, AE_PER_FE, source, refund, receiver));
        assertEquals(0, transfer.push(1, AE_PER_FE, source, refund, receiver));
        assertEquals(7, accepted[0]);
        assertEquals(16.5, ae[0]);
    }

    @Test
    void exportsOnlyWhatRealExtractionFundsAndReturnsFraction() {
        var transfer = new LoopTickerEnergyTransfer();
        double[] refunded = {0};
        assertEquals(3, transfer.push(0, AE_PER_FE, (amount, simulate) -> simulate ? 10 : 1.8,
                amount -> { refunded[0] += amount; return 0; },
                (amount, simulate) -> {
                    if (!simulate) assertEquals(3, amount);
                    return amount;
                }));
        assertEquals(0.3, refunded[0], 1e-12);
    }
}
