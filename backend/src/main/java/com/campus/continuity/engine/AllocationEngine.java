package com.campus.continuity.engine;

import com.campus.continuity.engine.AllocationOutcome.*;
import com.campus.continuity.engine.Compat.Reason;
import com.campus.continuity.engine.Compat.SlotNode;
import com.campus.continuity.engine.Model.*;
import java.util.*;

/**
 * Builds the allocation network  Source → Teams → (Lab,Slot) nodes → Sink  and solves it with {@link EdmondsKarp}.
 * Objective: MAXIMUM CARDINALITY (number of teams placed). Preferences and distances are NOT optimised.
 * Capacities: source→team = 1; team→slot = 1 iff {@link Compat#check} is OK; slot→sink = lab capacity.
 */
public final class AllocationEngine {
    private AllocationEngine() {}

    public static AllocationOutcome solve(Snapshot snap, boolean trace) {
        long b0 = System.nanoTime();
        List<Team> teams = snap.teams();
        List<SlotNode> slots = Compat.slotNodes(snap);
        int T = teams.size(), S = slots.size();
        int source = 0, sink = T + S + 1;
        FlowNetwork g = new FlowNetwork(T + S + 2);
        List<int[]> teamSlotEdges = new ArrayList<>(); // {edgeId, teamIdx, slotIdx}
        for (int i = 0; i < T; i++) g.addEdge(source, 1 + i, 1);
        for (int i = 0; i < T; i++)
            for (int j = 0; j < S; j++) {
                SlotNode sn = slots.get(j);
                if (Compat.check(teams.get(i), sn.lab(), sn.slot()) == Reason.OK)
                    teamSlotEdges.add(new int[]{g.addEdge(1 + i, 1 + T + j, 1), i, j});
            }
        for (int j = 0; j < S; j++) g.addEdge(1 + T + j, sink, slots.get(j).capacity());
        long buildNanos = System.nanoTime() - b0;

        EdmondsKarp.Result r = EdmondsKarp.run(g, source, sink, trace);

        // ---- assignments: team→slot edges carrying flow ----
        boolean[] placed = new boolean[T];
        int[] used = new int[S];
        List<Assignment> assignments = new ArrayList<>();
        for (int[] e : teamSlotEdges)
            if (r.edgeFlows()[e[0]] == 1) {
                placed[e[1]] = true; used[e[2]]++;
                SlotNode sn = slots.get(e[2]);
                assignments.add(new Assignment(teams.get(e[1]).code(), sn.lab().code(), sn.slot().code()));
            }
        // ---- unallocated with reasons ----
        List<Unallocated> un = new ArrayList<>();
        for (int i = 0; i < T; i++) if (!placed[i]) un.add(explainTeam(snap, teams.get(i)));

        // ---- utilisation ----
        int seats = 0;
        List<SlotLoad> loads = new ArrayList<>();
        Map<String, int[]> perLab = new LinkedHashMap<>();
        for (int j = 0; j < S; j++) {
            SlotNode sn = slots.get(j);
            seats += sn.capacity();
            loads.add(new SlotLoad(sn.lab().code(), sn.slot().code(), sn.capacity(), used[j]));
            int[] a = perLab.computeIfAbsent(sn.lab().code(), k -> new int[2]);
            a[0] += sn.capacity(); a[1] += used[j];
        }
        List<LabLoad> labLoads = new ArrayList<>();
        perLab.forEach((k, a) -> labLoads.add(new LabLoad(k, a[0], a[1], a[0] == 0 ? 0 : (double) a[1] / a[0])));

        Bottleneck bn = bottleneck(g, r, teams, slots, T, sink, un);

        Graph graph = null; Terminal term = null;
        if (trace) {
            List<Node> nodes = new ArrayList<>();
            nodes.add(new Node(source, "SOURCE", "Source", null, null));
            for (int i = 0; i < T; i++) nodes.add(new Node(1 + i, "TEAM", teams.get(i).code(), null, null));
            for (int j = 0; j < S; j++) nodes.add(new Node(1 + T + j, "SLOT", slots.get(j).label(), slots.get(j).lab().code(), slots.get(j).slot().code()));
            nodes.add(new Node(sink, "SINK", "Sink", null, null));
            List<Edge> edges = new ArrayList<>();
            for (int id = 0; id < g.edgeCount(); id++) edges.add(new Edge(id, g.from(id << 1), g.to(id << 1), g.original(id << 1)));
            graph = new Graph(nodes, edges, source, sink);
            List<Integer> side = new ArrayList<>();
            for (int v = 0; v < r.sourceSide().length; v++) if (r.sourceSide()[v]) side.add(v);
            term = new Terminal(r.finalVisitOrder(), side.stream().mapToInt(Integer::intValue).toArray(), r.edgeFlows(), r.cutEdgeIds(), r.maxFlow());
        }
        OpStats st = new OpStats(g.nodeCount(), g.edgeCount(), r.bfsRuns(), r.nodesVisited(), r.edgesScanned(), r.augmentations(), buildNanos, r.nanos());
        return new AllocationOutcome(T, r.maxFlow(), T - r.maxFlow(), assignments, un, seats, r.maxFlow(),
                seats == 0 ? 0 : (double) r.maxFlow() / seats, loads, labLoads, st, bn, trace, graph, trace ? r.steps() : null, term);
    }

    /** Why a team that the optimum could not place has no seat: no compatible option, or every option is saturated. */
    static Unallocated explainTeam(Snapshot snap, Team t) {
        if (snap.labs().isEmpty() || snap.slots().isEmpty())
            return new Unallocated(t.code(), "NO_RESOURCES", "No laboratories or time slots are defined.");
        EnumMap<Reason, Integer> counts = new EnumMap<>(Reason.class);
        for (Lab l : snap.labs()) for (Slot s : snap.slots()) counts.merge(Compat.check(t, l, s), 1, Integer::sum);
        if (counts.getOrDefault(Reason.OK, 0) > 0)
            return new Unallocated(t.code(), "CAPACITY_EXHAUSTED",
                    counts.get(Reason.OK) + " compatible lab-slot option(s) exist, but all are fully used by other teams in the optimal allocation.");
        StringBuilder sb = new StringBuilder("No compatible lab-slot option. Blocking reasons: ");
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
                .forEach(en -> sb.append(en.getValue()).append("× ").append(en.getKey().text).append("; "));
        return new Unallocated(t.code(), "NO_COMPATIBLE_SLOT", sb.toString().trim());
    }

    private static Bottleneck bottleneck(FlowNetwork g, EdmondsKarp.Result r, List<Team> teams, List<SlotNode> slots,
                                         int T, int sink, List<Unallocated> un) {
        int unallocated = un.size();
        boolean[] S = r.sourceSide();
        List<CutEdge> cut = new ArrayList<>();
        long cutValue = 0;
        for (int id : r.cutEdgeIds()) {
            int u = g.from(id << 1), v = g.to(id << 1), c = g.original(id << 1);
            String kind = u == 0 ? "TEAM_SUPPLY" : v == sink ? "SLOT_CAPACITY" : "TEAM_SLOT";
            cut.add(new CutEdge(nodeLabel(u, teams, slots, T, sink), nodeLabel(v, teams, slots, T, sink), c, kind));
            cutValue += c;
        }
        List<SlotRef> bslots = new ArrayList<>();
        for (int j = 0; j < slots.size(); j++)
            if (S[1 + T + j]) bslots.add(new SlotRef(slots.get(j).lab().code(), slots.get(j).slot().code(), slots.get(j).capacity()));
        int contendingAll = 0;
        for (int i = 0; i < T; i++) if (S[1 + i]) contendingAll++;
        long incompatible = un.stream().filter(u -> !u.category().equals("CAPACITY_EXHAUSTED")).count();
        long capacityLimited = unallocated - incompatible;
        int contending = (int) (contendingAll - incompatible); // teams that genuinely compete for capacity
        int cap = bslots.stream().mapToInt(SlotRef::capacity).sum();
        List<String> lines = new ArrayList<>();
        lines.add("Max-flow = " + r.maxFlow() + " equals the capacity of the minimum cut = " + cutValue + " (max-flow min-cut theorem).");
        if (unallocated == 0) {
            lines.add("Every team is allocated. The minimum cut consists of the source→team edges, so team demand — not laboratory capacity — is the limiting quantity.");
        } else {
            if (incompatible > 0)
                lines.add(incompatible + " team(s) have no compatible lab-slot option at all. No capacity change can place them; their equipment, size, duration or slot requirements must change, or a compatible resource must be added.");
            if (capacityLimited > 0) {
                lines.add(contending + " team(s) can only reach " + bslots.size() + " saturated lab-slot node(s) whose combined capacity is " + cap
                        + ", so at least " + (contending - cap) + " of them cannot be placed.");
                lines.add("The saturated nodes (every unit of capacity is used) are the capacity bottleneck: adding capacity there, or widening the compatibility of the teams that depend on them, is the only way to place more teams.");
            }
        }
        return new Bottleneck((int) cutValue, contending, Math.max(0, contending - cap), bslots, cut, lines);
    }

    private static String nodeLabel(int id, List<Team> teams, List<SlotNode> slots, int T, int sink) {
        if (id == 0) return "Source";
        if (id == sink) return "Sink";
        return id <= T ? teams.get(id - 1).code() : slots.get(id - 1 - T).label();
    }
}
