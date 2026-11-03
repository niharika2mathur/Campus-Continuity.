package com.campus.continuity.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.campus.continuity.engine.*;
import org.junit.jupiter.api.*;

/** Observatory presets are inputs only; these tests check the engine's results on them (PR-1 … PR-3). */
class PresetInstancesTest {
    @Test @DisplayName("PR-1 greedy-trap preset: Edmonds–Karp places both teams via an augmenting path using a reverse edge")
    void greedyTrap() {
        var o = AllocationEngine.solve(PresetInstances.get("greedy-trap").snapshot(), true);
        assertEquals(2, o.allocated());
        assertTrue(o.steps().stream().anyMatch(s -> { for (boolean r : s.pathReverse()) if (r) return true; return false; }), "a reverse residual edge must be used");
        assertEquals(1, GreedyAllocator.allocate(PresetInstances.get("greedy-trap").snapshot(), GreedyAllocator.Order.INPUT).allocated());
    }
    @Test @DisplayName("PR-2 bottleneck preset: min cut equals max flow and a saturated slot is reported")
    void bottleneck() {
        var o = AllocationEngine.solve(PresetInstances.get("bottleneck").snapshot(), true);
        assertEquals(o.allocated(), o.bottleneck().minCutValue());
        assertTrue(o.unallocatedCount() > 0 && !o.bottleneck().slots().isEmpty());
    }
    @Test @DisplayName("PR-3 mini-campus preset: incompatible team is flagged separately from capacity-limited teams")
    void miniCampus() {
        var o = AllocationEngine.solve(PresetInstances.get("mini-campus").snapshot(), false);
        assertTrue(o.unallocated().stream().anyMatch(u -> u.team().equals("X1") && u.category().equals("NO_COMPATIBLE_SLOT")));
        assertThrows(IllegalArgumentException.class, () -> PresetInstances.get("nope"));
    }
}
