package com.campus.continuity.sim;

import com.campus.continuity.engine.AllocationEngine;
import com.campus.continuity.engine.AllocationOutcome;
import com.campus.continuity.engine.AllocationOutcome.Assignment;
import com.campus.continuity.engine.Model;
import com.campus.continuity.engine.Model.*;
import java.util.*;

/**
 * What-if simulator. Applies mutations to an immutable {@link Snapshot} copy and re-runs Edmonds–Karp on BOTH the
 * baseline and the mutated snapshot on every call — results are never cached, so they cannot be stale.
 */
public final class ScenarioSimulator {
    private ScenarioSimulator() {}

    public record Applied(Snapshot snapshot, List<String> changes) {}
    public record Moved(String team, String fromLab, String fromSlot, String toLab, String toSlot) {}
    public record Diff(int allocatedDelta, int unallocatedDelta, double utilizationDelta, List<String> newlyDisplaced,
                       List<String> newlyRecovered, List<Moved> reassigned, List<String> bottleneckAdded,
                       List<String> bottleneckRemoved, List<String> bottleneckPersisting) {}
    public record ScenarioResult(List<String> changes, AllocationOutcome before, AllocationOutcome after, Diff diff) {}

    public static ScenarioResult run(Snapshot base, List<Mutation> mutations) {
        Applied a = apply(base, mutations);
        AllocationOutcome before = AllocationEngine.solve(base, false);
        AllocationOutcome after = AllocationEngine.solve(a.snapshot(), false);
        return new ScenarioResult(a.changes(), before, after, diff(before, after));
    }

    public static Applied apply(Snapshot base, List<Mutation> mutations) {
        if (mutations == null || mutations.isEmpty()) throw new IllegalArgumentException("A scenario needs at least one change");
        List<Lab> labs = new ArrayList<>(base.labs());
        List<Slot> slots = new ArrayList<>(base.slots());
        List<Team> teams = new ArrayList<>(base.teams());
        List<String> log = new ArrayList<>();
        for (Mutation m : mutations) {
            if (m == null || m.type() == null) throw new IllegalArgumentException("Mutation type is required");
            switch (m.type()) {
                case DISABLE_LAB -> { int i = labIdx(labs, m.target()); String r = reason(m, "Disabled in simulation");
                    labs.set(i, labs.get(i).withStatus(Availability.DISRUPTED, r)); log.add("Disabled laboratory " + labs.get(i).code() + " (" + r + ")"); }
                case RESTORE_LAB -> { int i = labIdx(labs, m.target());
                    labs.set(i, labs.get(i).withStatus(Availability.AVAILABLE, null)); log.add("Restored laboratory " + labs.get(i).code()); }
                case REDUCE_CAPACITY -> { int i = labIdx(labs, m.target()); int d = positive(m, "amount");
                    int c = Math.max(0, labs.get(i).capacity() - d);
                    labs.set(i, labs.get(i).withCapacity(c)); log.add("Reduced capacity of " + labs.get(i).code() + " by " + d + " to " + c); }
                case INCREASE_CAPACITY -> { int i = labIdx(labs, m.target()); int d = positive(m, "amount");
                    int c = labs.get(i).capacity() + d;
                    labs.set(i, labs.get(i).withCapacity(c)); log.add("Increased capacity of " + labs.get(i).code() + " by " + d + " to " + c); }
                case DISABLE_SLOT -> { int i = slotIdx(slots, m.target()); String r = reason(m, "Disabled in simulation");
                    slots.set(i, slots.get(i).withStatus(Availability.DISRUPTED, r)); log.add("Disabled time slot " + slots.get(i).code() + " (" + r + ")"); }
                case RESTORE_SLOT -> { int i = slotIdx(slots, m.target());
                    slots.set(i, slots.get(i).withStatus(Availability.AVAILABLE, null)); log.add("Restored time slot " + slots.get(i).code()); }
                case ADD_EQUIPMENT, REMOVE_EQUIPMENT -> {
                    int i = labIdx(labs, m.target());
                    Set<String> tags = new TreeSet<>(labs.get(i).equipment());
                    Set<String> delta = normTags(m.equipment());
                    if (delta.isEmpty()) throw new IllegalArgumentException("Equipment list is required for " + m.type());
                    if (m.type() == Mutation.Type.ADD_EQUIPMENT) tags.addAll(delta); else tags.removeAll(delta);
                    labs.set(i, labs.get(i).withEquipment(tags));
                    log.add((m.type() == Mutation.Type.ADD_EQUIPMENT ? "Added " : "Removed ") + delta + " on " + labs.get(i).code()); }
                case ADD_TEAMS -> {
                    String prefix = m.teamCode() == null || m.teamCode().isBlank() ? "SIM" : m.teamCode().trim();
                    int n = m.count() == null ? 1 : m.count();
                    if (n < 1 || n > 5000) throw new IllegalArgumentException("Team count must be between 1 and 5000");
                    Set<String> existing = new HashSet<>();
                    for (Team t : teams) existing.add(t.code().toLowerCase(Locale.ROOT));
                    for (int k = 1; k <= n; k++) {
                        String code = n == 1 ? prefix : prefix + "-" + String.format("%03d", k);
                        if (!existing.add(code.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Team code already exists: " + code);
                        teams.add(new Team(code, code, m.teamSize() == null ? 3 : m.teamSize(), toSet(m.requirements()),
                                m.durationMinutes() == null ? 60 : m.durationMinutes(), toSet(m.eligibleSlots())));
                    }
                    log.add("Added " + n + " team(s) with prefix " + prefix); }
            }
        }
        return new Applied(new Snapshot(labs, slots, teams), log);
    }

    private static Set<String> toSet(List<String> l) { return l == null ? Set.of() : new TreeSet<>(l); }
    private static Set<String> normTags(List<String> l) { return Model.normTags(l); }
    private static String reason(Mutation m, String d) { return m.reason() == null || m.reason().isBlank() ? d : m.reason().trim(); }
    private static int positive(Mutation m, String f) {
        if (m.amount() == null || m.amount() < 1) throw new IllegalArgumentException(m.type() + " requires a positive " + f);
        return m.amount();
    }
    private static int labIdx(List<Lab> l, String code) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).code().equalsIgnoreCase(code == null ? "" : code.trim())) return i;
        throw new IllegalArgumentException("Unknown laboratory: " + code);
    }
    private static int slotIdx(List<Slot> l, String code) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).code().equalsIgnoreCase(code == null ? "" : code.trim())) return i;
        throw new IllegalArgumentException("Unknown time slot: " + code);
    }

    static Diff diff(AllocationOutcome b, AllocationOutcome a) {
        Map<String, Assignment> bm = new LinkedHashMap<>(), am = new LinkedHashMap<>();
        b.assignments().forEach(x -> bm.put(x.team(), x));
        a.assignments().forEach(x -> am.put(x.team(), x));
        List<String> displaced = new ArrayList<>(), recovered = new ArrayList<>();
        List<Moved> moved = new ArrayList<>();
        for (var e : bm.entrySet()) {
            Assignment n = am.get(e.getKey());
            if (n == null) displaced.add(e.getKey());
            else if (!n.lab().equals(e.getValue().lab()) || !n.slot().equals(e.getValue().slot()))
                moved.add(new Moved(e.getKey(), e.getValue().lab(), e.getValue().slot(), n.lab(), n.slot()));
        }
        for (String t : am.keySet()) if (!bm.containsKey(t)) recovered.add(t);
        Set<String> bb = new TreeSet<>(), ab = new TreeSet<>();
        b.bottleneck().slots().forEach(s -> bb.add(s.lab() + " / " + s.slot()));
        a.bottleneck().slots().forEach(s -> ab.add(s.lab() + " / " + s.slot()));
        List<String> added = new ArrayList<>(ab), removed = new ArrayList<>(bb), same = new ArrayList<>(bb);
        added.removeAll(bb); removed.removeAll(ab); same.retainAll(ab);
        return new Diff(a.allocated() - b.allocated(), a.unallocatedCount() - b.unallocatedCount(),
                a.utilization() - b.utilization(), displaced, recovered, moved, added, removed, same);
    }
}
