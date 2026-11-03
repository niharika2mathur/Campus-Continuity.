package com.campus.continuity.engine;

import java.util.List;

/** Everything the API reports about one Edmonds–Karp allocation run. graph/steps/terminal are null unless a trace was requested. */
public record AllocationOutcome(
        int totalTeams, int allocated, int unallocatedCount,
        List<Assignment> assignments, List<Unallocated> unallocated,
        int totalSeats, int usedSeats, double utilization,
        List<SlotLoad> slotLoads, List<LabLoad> labLoads,
        OpStats stats, Bottleneck bottleneck,
        boolean traceIncluded, Graph graph, List<EdmondsKarp.TraceStep> steps, Terminal terminal) {

    /** Copy without the (large) trace, used when persisting run history. */
    public AllocationOutcome withoutTrace() {
        return new AllocationOutcome(totalTeams, allocated, unallocatedCount, assignments, unallocated, totalSeats, usedSeats,
                utilization, slotLoads, labLoads, stats, bottleneck, false, null, null, null);
    }

    public record Assignment(String team, String lab, String slot) {}
    public record Unallocated(String team, String category, String detail) {}
    public record SlotLoad(String lab, String slot, int capacity, int used) {}
    public record LabLoad(String lab, int seats, int used, double utilization) {}
    public record OpStats(int nodes, int edges, long bfsRuns, long nodesVisited, long edgesScanned,
                          long augmentations, long buildNanos, long flowNanos) {}
    public record SlotRef(String lab, String slot, int capacity) {}
    public record CutEdge(String from, String to, int capacity, String kind) {}
    public record Bottleneck(int minCutValue, int contendingTeams, int deficit, List<SlotRef> slots,
                             List<CutEdge> cutEdges, List<String> lines) {}
    public record Node(int id, String type, String label, String lab, String slot) {}
    public record Edge(int id, int from, int to, int capacity) {}
    public record Graph(List<Node> nodes, List<Edge> edges, int source, int sink) {}
    /** Final failing BFS: visit order, nodes of the min-cut source side S, final flows and cut edge ids. */
    public record Terminal(int[] visitOrder, int[] sourceSide, int[] edgeFlows, int[] cutEdgeIds, int maxFlow) {}
}
