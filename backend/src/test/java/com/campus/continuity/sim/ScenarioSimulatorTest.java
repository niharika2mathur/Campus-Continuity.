package com.campus.continuity.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.campus.continuity.engine.Model.*;
import com.campus.continuity.sim.Mutation.Type;
import org.junit.jupiter.api.*;
import java.util.*;

/** What-if simulator tests (SIM-1 … SIM-8). */
class ScenarioSimulatorTest {
    private static Mutation m(Type t, String target, Integer amount) { return new Mutation(t, target, amount, "test", null, null, null, null, null, null, null); }
    private static Lab lab(String c, int cap, String... eq) { return new Lab(c, c, cap, 6, Set.of(eq), Availability.AVAILABLE, null); }
    private static Team team(String c, String... req) { return new Team(c, c, 3, Set.of(req), 60, Set.of()); }
    private Snapshot base() {
        return new Snapshot(List.of(lab("A", 2, "gpu"), lab("B", 2)), List.of(new Slot("s1", "s1", 120, Availability.AVAILABLE, null)),
                List.of(team("t1"), team("t2"), team("t3"), team("t4"), team("g1", "gpu")));
    }

    @Test @DisplayName("SIM-1 disabling a lab displaces teams and reduces allocation; the algorithm is re-run")
    void disableLab() {
        var r = ScenarioSimulator.run(base(), List.of(m(Type.DISABLE_LAB, "B", null)));
        assertEquals(4, r.before().allocated()); assertEquals(2, r.after().allocated());
        assertEquals(-2, r.diff().allocatedDelta()); assertEquals(2, r.diff().newlyDisplaced().size());
    }

    @Test @DisplayName("SIM-2 capacity increase recovers displaced teams")
    void increase() {
        var s = ScenarioSimulator.apply(base(), List.of(m(Type.DISABLE_LAB, "B", null))).snapshot();
        var r = ScenarioSimulator.run(s, List.of(m(Type.INCREASE_CAPACITY, "A", 3)));
        assertTrue(r.diff().allocatedDelta() > 0); assertFalse(r.diff().newlyRecovered().isEmpty());
    }

    @Test @DisplayName("SIM-3 restoring a disabled lab recovers teams")
    void restore() {
        var s = ScenarioSimulator.apply(base(), List.of(m(Type.DISABLE_LAB, "B", null))).snapshot();
        assertEquals(2, ScenarioSimulator.run(s, List.of(m(Type.RESTORE_LAB, "B", null))).diff().allocatedDelta());
    }

    @Test @DisplayName("SIM-4 changing compatibility (remove equipment) strands the team that needed it")
    void compatibility() {
        Snapshot s = new Snapshot(List.of(lab("A", 1, "gpu")), List.of(new Slot("s1", "s1", 120, Availability.AVAILABLE, null)), List.of(team("g1", "gpu")));
        var r = ScenarioSimulator.run(s, List.of(new Mutation(Type.REMOVE_EQUIPMENT, "A", null, null, List.of("GPU"), null, null, null, null, null, null)));
        assertEquals(List.of("g1"), r.diff().newlyDisplaced());
        assertEquals(1, r.before().allocated()); assertEquals(0, r.after().allocated());
    }

    @Test @DisplayName("SIM-5 adding teams beyond capacity leaves them unallocated and moves the bottleneck")
    void addTeams() {
        var r = ScenarioSimulator.run(base(), List.of(new Mutation(Type.ADD_TEAMS, "SURGE", null, null, null, "SURGE", 3, null, 60, null, 10)));
        assertEquals(15, r.after().totalTeams()); assertTrue(r.after().unallocatedCount() > r.before().unallocatedCount());
    }

    @Test @DisplayName("SIM-6 INVALID: unknown lab, empty scenario, zero amount, duplicate generated team code")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioSimulator.run(base(), List.of(m(Type.DISABLE_LAB, "NOPE", null))));
        assertThrows(IllegalArgumentException.class, () -> ScenarioSimulator.run(base(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> ScenarioSimulator.run(base(), List.of(m(Type.REDUCE_CAPACITY, "A", 0))));
        assertThrows(IllegalArgumentException.class, () -> ScenarioSimulator.run(base(), List.of(new Mutation(Type.ADD_TEAMS, null, null, null, null, "t1", 3, null, 60, null, 1))));
    }

    @Test @DisplayName("SIM-7 BOUNDARY: reducing capacity below zero clamps at zero, never negative")
    void clamp() {
        var s = ScenarioSimulator.apply(base(), List.of(m(Type.REDUCE_CAPACITY, "A", 99))).snapshot();
        assertEquals(0, s.labs().get(0).capacity());
    }

    @Test @DisplayName("SIM-8 the baseline snapshot is never mutated (scenario reset is free) and results are recomputed each call")
    void immutability() {
        Snapshot b = base();
        var r1 = ScenarioSimulator.run(b, List.of(m(Type.DISABLE_LAB, "A", null)));
        var r2 = ScenarioSimulator.run(b, List.of(m(Type.DISABLE_LAB, "A", null)));
        assertEquals(Availability.AVAILABLE, b.labs().get(0).status());
        assertEquals(r1.after().allocated(), r2.after().allocated());
    }

    @Test @DisplayName("SIM-9 disabling a time slot removes all its seats")
    void disableSlot() {
        var r = ScenarioSimulator.run(base(), List.of(m(Type.DISABLE_SLOT, "s1", null)));
        assertEquals(0, r.after().allocated());
    }
}
