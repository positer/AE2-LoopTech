package com.example.ae2lightoptimizer.world;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BatchAllocationTest {
    @Test
    void reassignsOverlappingIngredientsToReachTheMaximum() {
        var stock = new int[]{4, 3, 1};
        var demands = List.of(new BatchAllocation.Demand(0b011, 1, 0),
                new BatchAllocation.Demand(0b001, 1, 0),
                new BatchAllocation.Demand(0b100, 0, 1));
        var result = BatchAllocation.maximum(stock, demands, 4);
        assertEquals(3, result.batches());
        assertEquals(6, result.consumed()[0] + result.consumed()[1]);
        assertEquals(0, result.consumed()[2]);
    }

    @Test
    void reservesCatalystsThatMatchAConsumedIngredient() {
        var demand = List.of(new BatchAllocation.Demand(0b001, 2, 1));
        assertNull(BatchAllocation.maximum(new int[]{2}, demand, 10));
        var result = BatchAllocation.maximum(new int[]{8}, demand, 10);
        assertEquals(3, result.batches());
        assertArrayEquals(new int[]{6}, result.consumed());
    }

    @Test
    void respectsTheOutputBound() {
        var result = BatchAllocation.maximum(new int[]{64}, List.of(new BatchAllocation.Demand(1, 1, 0)), 2);
        assertEquals(2, result.batches());
        assertArrayEquals(new int[]{2}, result.consumed());
    }
}
