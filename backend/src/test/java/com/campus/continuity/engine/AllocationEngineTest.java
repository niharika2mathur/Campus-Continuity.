package com.campus.continuity.engine;

import static com.campus.continuity.engine.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import com.campus.continuity.engine.Model.*;
import org.junit.jupiter.api.*;
import java.util.*;

/** Allocation-model tests (AL-1 … AL-8) and greedy baseline tests (GR-1 … GR-5). */
class AllocationEngineTest {

    @Test @DisplayName("AL-1 BOUNDARY: empty snapshot yields an empty, consistent outcome")
    void empty() {
        var o = AllocationEngine.solve(new Snapshot(List.of(), List.of(), List.of()), true);
        assertEquals(0, o.allocated()); assertEquals(0, o.totalTeams()); assertEquals(0.0, o.utilization());
    }

    @Test @DisplayName("AL-2 all teams fit: allocation count equals team count, no unallocated")
    void allFit() {
        var snap = new Snapshot(List.of(lab("A", 2, 5)), List.of(slot("s1"), slot("s2")), List.of(team("t1"), team("t2"), team("t3")));
        var o = AllocationEngine.solve(snap, false);
        assertEquals(3, o.allocated()); assertTrue(o.unallocated().isEmpty());
        assertEquals(3.0 / 4.0, o.utilization(), 1e-9);
    }

    @Test @DisplayName("AL-3 incompatible team (missing equipment) is reported with the reason, not silently dropped")
    void incompatible() {
        var snap = new Snapshot(List.of(lab("A", 2, 5, "gpu")), List.of(slot("s1")),
                List.of(team("ok", 3, 60, new String[]{"gpu"}), team("bad", 3, 60, new String[]{"fpga"})));
        var o = AllocationEngine.solve(snap, false);
        assertEquals(1, o.allocated());
        assertEquals("NO_COMPATIBLE_SLOT", o.unallocated().get(0).category());
        assertTrue(o.unallocated().get(0).detail().contains("equipment"));
    }

    @Test @DisplayName("AL-4 zero-capacity and disrupted labs offer no seats")
    void zeroCapacityAndDisruption() {
        var l0 = lab("Z", 0, 5); var ld = lab("D", 3, 5).withStatus(Availability.DISRUPTED, "flood");
        var o = AllocationEngine.solve(new Snapshot(List.of(l0, ld), List.of(slot("s1")), List.of(team("t1"))), false);
        assertEquals(0, o.allocated()); assertEquals(0, o.totalSeats());
    }

    @Test @DisplayName("AL-5 OPTIMALITY: the greedy trap instance — Edmonds–Karp places 2 teams, input-order greedy only 1")
    void greedyTrap() {
        var snap = new Snapshot(List.of(lab("A", 1, 5)), List.of(slot("s1"), slot("s2")),
                List.of(team("flex", "s1", "s2"), team("rigid", "s1")));
        assertEquals(2, AllocationEngine.solve(snap, false).allocated());
        assertEquals(1, GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT).allocated());
        assertEquals(2, GreedyAllocator.allocate(snap, GreedyAllocator.Order.MOST_CONSTRAINED_FIRST).allocated());
    }

    @Test @DisplayName("AL-6 bottleneck explanation: saturated slot appears in the min cut and deficit equals unallocated count")
    void bottleneck() {
        var snap = new Snapshot(List.of(lab("A", 1, 5)), List.of(slot("s1")), List.of(team("t1"), team("t2"), team("t3")));
        var o = AllocationEngine.solve(snap, true);
        assertEquals(1, o.allocated()); assertEquals(2, o.bottleneck().deficit());
        assertEquals(1, o.bottleneck().slots().size()); assertEquals(1, o.bottleneck().minCutValue());
        assertEquals("CAPACITY_EXHAUSTED", o.unallocated().get(0).category());
        assertEquals(o.allocated(), o.terminal().maxFlow());
    }

    @Test @DisplayName("AL-7 INVALID: duplicate codes and negative capacities are rejected at model construction")
    void invalidModel() {
        assertThrows(IllegalArgumentException.class, () -> lab("A", -1, 5));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(), List.of(), List.of(team("t"), team("T"))));
        assertThrows(IllegalArgumentException.class, () -> new Team("t", "t", 0, Set.of(), 60, Set.of()));
    }

    @Test @DisplayName("AL-8 RANDOM CROSS-CHECK: matches an exhaustive backtracking optimum on 150 small instances; greedy never beats it")
    void bruteForce() {
        Random r = new Random(7);
        for (int it = 0; it < 150; it++) {
            int nt = 1 + r.nextInt(7);
            List<Slot> slots = List.of(slot("s0"), slot("s1"), slot("s2"));
            List<Lab> labs = List.of(lab("A", 1 + r.nextInt(2), 5, "x"), lab("B", 1 + r.nextInt(2), 4));
            List<Team> teams = new ArrayList<>();
            for (int i = 0; i < nt; i++) {
                Set<String> el = new TreeSet<>(); for (int s = 0; s < 3; s++) if (r.nextBoolean()) el.add("s" + s);
                teams.add(new Team("t" + i, "t", 2 + r.nextInt(4), r.nextInt(4) == 0 ? Set.of("x") : Set.of(), 60, el));
            }
            Snapshot snap = new Snapshot(labs, slots, teams);
            int ek = AllocationEngine.solve(snap, false).allocated();
            assertEquals(brute(snap, 0, new int[Compat.slotNodes(snap).size()]), ek, "iteration " + it);
            assertTrue(GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT).allocated() <= ek);
        }
    }

    @Test @DisplayName("AL-9 LARGE: 1,000 generated teams (≈88k edges) solve within 15 s and never exceed capacity")
    void large() {
        Snapshot snap = com.campus.continuity.bench.DatasetGenerator.generate(1000, 1L);
        assertTimeout(java.time.Duration.ofSeconds(15), () -> {
            var o = AllocationEngine.solve(snap, false);
            assertTrue(o.allocated() <= o.totalSeats());
            for (var l : o.slotLoads()) assertTrue(l.used() <= l.capacity());
            assertEquals(o.allocated(), o.assignments().size());
        });
    }

    @Test @DisplayName("GR-1 greedy never exceeds slot capacity and places each team at most once")
    void greedyFeasible() {
        Snapshot snap = com.campus.continuity.bench.DatasetGenerator.generate(300, 3L);
        for (var order : GreedyAllocator.Order.values()) {
            var g = GreedyAllocator.allocate(snap, order);
            Set<String> seen = new HashSet<>(); Map<String, Integer> load = new HashMap<>();
            for (var a : g.assignments()) { assertTrue(seen.add(a.team())); load.merge(a.lab() + "/" + a.slot(), 1, Integer::sum); }
            for (var l : snap.labs()) for (var s : snap.slots()) assertTrue(load.getOrDefault(l.code() + "/" + s.code(), 0) <= l.capacity());
            assertEquals(g.allocated() + g.unallocated().size(), 300);
        }
    }

    @Test @DisplayName("GR-2 greedy result depends on team ordering (different orders, different counts on the trap)")
    void greedyOrderDependence() {
        var snap = new Snapshot(List.of(lab("A", 1, 5)), List.of(slot("s1"), slot("s2")), List.of(team("flex", "s1", "s2"), team("rigid", "s1")));
        assertNotEquals(GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT).allocated(),
                GreedyAllocator.allocate(snap, GreedyAllocator.Order.REVERSED).allocated());
    }

    @Test @DisplayName("GR-3 BOUNDARY: greedy on empty input returns zero")
    void greedyEmpty() { assertEquals(0, GreedyAllocator.allocate(new Snapshot(List.of(), List.of(), List.of()), GreedyAllocator.Order.INPUT).allocated()); }

    @Test @DisplayName("GR-4 greedy ≤ Edmonds–Karp on 20 generated datasets (EK is optimal for cardinality)")
    void greedyNeverBetter() {
        for (long seed = 0; seed < 20; seed++) {
            Snapshot snap = com.campus.continuity.bench.DatasetGenerator.generate(120, seed);
            int ek = AllocationEngine.solve(snap, false).allocated();
            for (var o : GreedyAllocator.Order.values()) assertTrue(GreedyAllocator.allocate(snap, o).allocated() <= ek);
        }
    }

    @Test @DisplayName("GR-5 greedy respects unavailable resources")
    void greedyUnavailable() {
        var snap = new Snapshot(List.of(lab("A", 3, 5).withStatus(Availability.DISRUPTED, "x")), List.of(slot("s1")), List.of(team("t")));
        assertEquals(0, GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT).allocated());
    }

    // exhaustive maximum matching with capacities
    private static int brute(Snapshot snap, int i, int[] used) {
        if (i == snap.teams().size()) return 0;
        var nodes = Compat.slotNodes(snap);
        int best = brute(snap, i + 1, used); // skip team
        for (int j = 0; j < nodes.size(); j++) {
            var n = nodes.get(j);
            if (used[j] < n.capacity() && Compat.check(snap.teams().get(i), n.lab(), n.slot()) == Compat.Reason.OK) {
                used[j]++; best = Math.max(best, 1 + brute(snap, i + 1, used)); used[j]--;
            }
        }
        return best;
    }
}
