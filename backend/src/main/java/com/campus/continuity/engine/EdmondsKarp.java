package com.campus.continuity.engine;

import java.util.*;

/**
 * Edmonds–Karp: Ford–Fulkerson where each augmenting path is a SHORTEST (fewest edges) s→t path in the residual
 * graph, found by breadth-first search. Complexity O(V·E²); at most O(V·E) augmentations.
 * Written from scratch for this project (no graph library).
 */
public final class EdmondsKarp {
    private EdmondsKarp() {}

    /** One augmentation. Edge ids index the forward edges; pathReverse[i] marks traversal of a reverse (residual) arc. */
    public record TraceStep(int iteration, int[] visitOrder, int[] pathNodes, int[] pathEdges, boolean[] pathReverse,
                            int bottleneck, int flowValue, int[] edgeFlows) {}

    public record Result(int maxFlow, int[] edgeFlows, List<TraceStep> steps, int[] finalVisitOrder,
                         boolean[] sourceSide, int[] cutEdgeIds,
                         long bfsRuns, long nodesVisited, long edgesScanned, long augmentations, long nanos) {}

    public static Result run(FlowNetwork g, int s, int t, boolean trace) {
        int n = g.nodeCount();
        if (s < 0 || s >= n || t < 0 || t >= n) throw new IllegalArgumentException("Source/sink out of range");
        if (s == t) throw new IllegalArgumentException("Source and sink must differ");
        long t0 = System.nanoTime();
        long bfsRuns = 0, visited = 0, scanned = 0, augs = 0;
        int flow = 0;
        int[] parentArc = new int[n];
        int[] queue = new int[n];
        List<TraceStep> steps = new ArrayList<>();
        int[] finalOrder = new int[0];
        boolean[] side = new boolean[n];

        while (true) {
            Arrays.fill(parentArc, -1);
            parentArc[s] = -2;
            int qh = 0, qt = 0;
            queue[qt++] = s;
            bfsRuns++;
            boolean found = false;
            while (qh < qt && !found) {
                int u = queue[qh++];
                visited++;
                for (int e = g.first(u); e != -1; e = g.next(e)) {
                    scanned++;
                    int v = g.to(e);
                    if (g.residual(e) > 0 && parentArc[v] == -1) {
                        parentArc[v] = e;
                        queue[qt++] = v;
                        if (v == t) { found = true; break; }
                    }
                }
            }
            int[] order = trace ? Arrays.copyOf(queue, qh) : null;
            if (!found) {
                // BFS exhausted: reachable set from s in the residual graph is the source side S of a minimum cut.
                for (int v = 0; v < n; v++) side[v] = parentArc[v] != -1;
                if (trace) finalOrder = order;
                break;
            }
            // bottleneck = minimum residual along the path
            int b = Integer.MAX_VALUE;
            for (int v = t; v != s; v = g.from(parentArc[v])) b = Math.min(b, g.residual(parentArc[v]));
            List<Integer> nodes = trace ? new ArrayList<>() : null;
            List<Integer> edges = trace ? new ArrayList<>() : null;
            List<Boolean> rev = trace ? new ArrayList<>() : null;
            for (int v = t; v != s; v = g.from(parentArc[v])) {
                int e = parentArc[v];
                g.push(e, b);
                if (trace) { nodes.add(v); edges.add(e >> 1); rev.add((e & 1) == 1); }
            }
            flow += b;
            augs++;
            if (trace) {
                nodes.add(s);
                Collections.reverse(nodes); Collections.reverse(edges); Collections.reverse(rev);
                boolean[] r = new boolean[rev.size()];
                for (int i = 0; i < r.length; i++) r[i] = rev.get(i);
                steps.add(new TraceStep((int) augs, order, nodes.stream().mapToInt(Integer::intValue).toArray(),
                        edges.stream().mapToInt(Integer::intValue).toArray(), r, b, flow, g.flows()));
            }
        }
        List<Integer> cut = new ArrayList<>();
        for (int id = 0; id < g.edgeCount(); id++) {
            int e = id << 1;
            if (side[g.from(e)] && !side[g.to(e)] && g.original(e) > 0) cut.add(id);
        }
        return new Result(flow, g.flows(), steps, finalOrder, side, cut.stream().mapToInt(Integer::intValue).toArray(),
                bfsRuns, visited, scanned, augs, System.nanoTime() - t0);
    }
}
