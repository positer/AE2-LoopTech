package com.example.ae2lightoptimizer.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Allocates inventory against overlapping ingredient predicates, including retained catalysts. */
final class BatchAllocation {
    record Demand(int slots, int consumed, int retained) {}
    record Result(int batches, int[] consumed) {}

    static Result maximum(int[] stock, List<Demand> demands, int limit) {
        Result best = null;
        int low = 1;
        while (low <= limit) {
            int batches = low + (limit - low) / 2;
            int[] consumed = allocate(stock, demands, batches);
            if (consumed == null) {
                limit = batches - 1;
            } else {
                best = new Result(batches, consumed);
                low = batches + 1;
            }
        }
        return best;
    }

    private static int[] allocate(int[] stock, List<Demand> demands, int batches) {
        int source = 0;
        int firstSlot = 1;
        int firstDemand = firstSlot + stock.length;
        int sink = firstDemand + 2 * demands.size();
        var edges = new ArrayList<List<Integer>>(sink + 1);
        var capacity = new int[sink + 1][sink + 1];
        for (int vertex = 0; vertex <= sink; vertex++) {
            edges.add(new ArrayList<>());
        }
        int total = 0;
        for (int slot = 0; slot < stock.length; slot++) {
            edge(edges, capacity, source, firstSlot + slot, stock[slot]);
        }
        for (int i = 0; i < demands.size(); i++) {
            Demand demand = demands.get(i);
            int consumed = demand.consumed * batches;
            int retained = demand.retained;
            int target = firstDemand + 2 * i;
            total += consumed + retained;
            edge(edges, capacity, target, sink, consumed);
            edge(edges, capacity, target + 1, sink, retained);
            for (int slot = 0; slot < stock.length; slot++) {
                if ((demand.slots & (1 << slot)) != 0) {
                    edge(edges, capacity, firstSlot + slot, target, consumed);
                    edge(edges, capacity, firstSlot + slot, target + 1, retained);
                }
            }
        }
        int[] parents = new int[sink + 1];
        int[] queue = new int[sink + 1];
        while (total > 0) {
            Arrays.fill(parents, -1);
            parents[source] = source;
            queue[0] = source;
            int end = 1;
            for (int start = 0; start < end && parents[sink] == -1; start++) {
                int from = queue[start];
                for (int to : edges.get(from)) {
                    if (parents[to] == -1 && capacity[from][to] > 0) {
                        parents[to] = from;
                        queue[end++] = to;
                    }
                }
            }
            if (parents[sink] == -1) {
                return null;
            }
            int amount = total;
            for (int to = sink; to != source; to = parents[to]) {
                amount = Math.min(amount, capacity[parents[to]][to]);
            }
            for (int to = sink; to != source; to = parents[to]) {
                int from = parents[to];
                capacity[from][to] -= amount;
                capacity[to][from] += amount;
            }
            total -= amount;
        }
        int[] consumed = new int[stock.length];
        for (int slot = 0; slot < stock.length; slot++) {
            for (int i = 0; i < demands.size(); i++) {
                consumed[slot] += capacity[firstDemand + 2 * i][slot + firstSlot];
            }
        }
        return consumed;
    }

    private static void edge(List<List<Integer>> edges, int[][] capacity, int from, int to, int amount) {
        if (amount > 0) {
            edges.get(from).add(to);
            edges.get(to).add(from);
            capacity[from][to] = amount;
        }
    }
}
