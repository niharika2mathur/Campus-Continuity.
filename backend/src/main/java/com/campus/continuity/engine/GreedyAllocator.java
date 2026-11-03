package com.campus.continuity.engine;

import com.campus.continuity.engine.AllocationOutcome.Assignment;
import com.campus.continuity.engine.Compat.Reason;
import com.campus.continuity.engine.Compat.SlotNode;
import com.campus.continuity.engine.Model.*;
import java.util.*;

/**
 * Baseline: first-fit greedy. Teams are processed once in a chosen order; each takes the first compatible slot node
 * with remaining capacity and is never moved. Does not reuse the flow network. Result depends on the order and is not
 * guaranteed optimal (it can never exceed Edmonds–Karp, which is optimal for maximum cardinality).
 */
public final class GreedyAllocator {
    private GreedyAllocator() {}

    public enum Order { INPUT, REVERSED, LARGEST_FIRST, MOST_CONSTRAINED_FIRST }

    public record Result(Order order, int allocated, int total, List<Assignment> assignments, List<String> unallocated,
                         long compatChecks, long nanos) {}

    public static Result allocate(Snapshot snap, Order order) {
        long t0 = System.nanoTime();
        List<Team> teams = new ArrayList<>(snap.teams());
        List<SlotNode> slots = Compat.slotNodes(snap);
        long checks = 0;
        switch (order) {
            case REVERSED -> Collections.reverse(teams);
            case LARGEST_FIRST -> teams.sort(Comparator.comparingInt(Team::size).reversed());
            case MOST_CONSTRAINED_FIRST -> {
                Map<String, Integer> options = new HashMap<>();
                for (Team t : teams) {
                    int c = 0;
                    for (SlotNode sn : slots) { checks++; if (Compat.check(t, sn.lab(), sn.slot()) == Reason.OK) c++; }
                    options.put(t.code(), c);
                }
                teams.sort(Comparator.comparingInt(t -> options.get(t.code())));
            }
            default -> { }
        }
        int[] remaining = new int[slots.size()];
        for (int j = 0; j < remaining.length; j++) remaining[j] = slots.get(j).capacity();
        List<Assignment> out = new ArrayList<>();
        List<String> un = new ArrayList<>();
        for (Team t : teams) {
            boolean ok = false;
            for (int j = 0; j < remaining.length && !ok; j++) {
                if (remaining[j] == 0) continue;
                SlotNode sn = slots.get(j);
                checks++;
                if (Compat.check(t, sn.lab(), sn.slot()) == Reason.OK) {
                    remaining[j]--; ok = true;
                    out.add(new Assignment(t.code(), sn.lab().code(), sn.slot().code()));
                }
            }
            if (!ok) un.add(t.code());
        }
        return new Result(order, out.size(), teams.size(), out, un, checks, System.nanoTime() - t0);
    }
}
