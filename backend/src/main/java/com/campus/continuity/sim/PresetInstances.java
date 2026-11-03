package com.campus.continuity.sim;

import com.campus.continuity.engine.Model.*;
import java.util.*;

/** Small, hand-designed instances for the Algorithm Observatory. They are INPUTS only — every result is computed by the real engine. */
public final class PresetInstances {
    private PresetInstances() {}

    public record Preset(String id, String title, String description, Snapshot snapshot) {}

    private static Lab lab(String c, int cap, int max, String... eq) { return new Lab(c, c, cap, max, Set.of(eq), Availability.AVAILABLE, null); }
    private static Slot slot(String c, int dur) { return new Slot(c, c, dur, Availability.AVAILABLE, null); }
    private static Team team(String c, int size, int dur, List<String> req, List<String> el) { return new Team(c, c, size, new TreeSet<>(req), dur, new TreeSet<>(el)); }

    public static List<Preset> all() {
        List<Preset> p = new ArrayList<>();
        // 1. greedy trap: flexible team listed first steals the only slot a rigid team can use
        p.add(new Preset("greedy-trap", "Greedy trap (reverse edges matter)",
                "A flexible team is listed first. First-fit greedy gives it S1 and strands the rigid team; Edmonds–Karp reroutes it via a reverse residual edge.",
                new Snapshot(List.of(lab("LAB-A", 1, 6)), List.of(slot("S1", 120), slot("S2", 120)),
                        List.of(team("FLEX", 3, 60, List.of(), List.of("S1", "S2")), team("RIGID", 3, 60, List.of(), List.of("S1"))))));
        // 2. capacity bottleneck
        p.add(new Preset("bottleneck", "Capacity bottleneck",
                "Five teams need the single GPU lab; two seats exist. The minimum cut isolates the saturated slot nodes.",
                new Snapshot(List.of(lab("GPU-LAB", 1, 6, "gpu"), lab("GEN-LAB", 2, 6)), List.of(slot("MON-AM", 120), slot("MON-PM", 120)),
                        List.of(team("T1", 3, 60, List.of("gpu"), List.of()), team("T2", 3, 60, List.of("gpu"), List.of()),
                                team("T3", 3, 60, List.of("gpu"), List.of()), team("T4", 2, 60, List.of(), List.of("MON-AM")),
                                team("T5", 2, 60, List.of(), List.of("MON-AM"))))));
        // 3. mixed campus with an incompatible team and a disconnected component
        p.add(new Preset("mini-campus", "Mini campus (incompatible team + augmenting detours)",
                "Three labs, three slots, eight teams including one that needs hardware nobody has.",
                new Snapshot(List.of(lab("NET-LAB", 2, 5, "linux"), lab("FPGA-LAB", 1, 4, "fpga", "linux"), lab("CAD-LAB", 2, 6, "cad")),
                        List.of(slot("A", 120), slot("B", 90), slot("C", 120)),
                        List.of(team("N1", 3, 60, List.of("linux"), List.of("A")), team("N2", 3, 60, List.of("linux"), List.of("A", "B")),
                                team("N3", 4, 90, List.of("linux"), List.of("B")), team("F1", 3, 60, List.of("fpga"), List.of("A", "C")),
                                team("F2", 3, 60, List.of("fpga"), List.of("A")), team("C1", 4, 60, List.of("cad"), List.of()),
                                team("C2", 5, 60, List.of("cad"), List.of("C")), team("X1", 3, 60, List.of("quantum-annealer"), List.of())))));
        return p;
    }

    public static Preset get(String id) {
        return all().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + id));
    }
}
