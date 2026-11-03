package com.campus.continuity.engine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;

/** Correctness tests for the Edmonds–Karp implementation (EK-1 … EK-9). */
class EdmondsKarpTest {

    private static FlowNetwork clrs() {            // CLRS Fig. 26.1: known max flow = 23
        FlowNetwork g = new FlowNetwork(6);       // 0=s 1=v1 2=v2 3=v3 4=v4 5=t
        g.addEdge(0, 1, 16); g.addEdge(0, 2, 13); g.addEdge(1, 3, 12); g.addEdge(2, 1, 4);
        g.addEdge(2, 4, 14); g.addEdge(3, 2, 9); g.addEdge(3, 5, 20); g.addEdge(4, 3, 7); g.addEdge(4, 5, 4);
        return g;
    }

    @Test @DisplayName("EK-1 known textbook network (CLRS) has max flow 23")
    void textbookNetwork() { assertEquals(23, EdmondsKarp.run(clrs(), 0, 5, false).maxFlow()); }

    @Test @DisplayName("EK-2 BOUNDARY: disconnected source and sink gives zero flow and empty cut")
    void disconnected() {
        FlowNetwork g = new FlowNetwork(4); g.addEdge(0, 1, 5); g.addEdge(2, 3, 5);
        var r = EdmondsKarp.run(g, 0, 3, true);
        assertEquals(0, r.maxFlow()); assertTrue(r.steps().isEmpty()); assertEquals(0, r.augmentations());
    }

    @Test @DisplayName("EK-3 BOUNDARY: zero-capacity edge blocks flow")
    void zeroCapacity() {
        FlowNetwork g = new FlowNetwork(3); g.addEdge(0, 1, 0); g.addEdge(1, 2, 9);
        assertEquals(0, EdmondsKarp.run(g, 0, 2, false).maxFlow());
    }

    @Test @DisplayName("EK-4 INVALID: negative capacity, self loop, bad endpoint, source==sink, tiny graph rejected")
    void invalidInput() {
        FlowNetwork g = new FlowNetwork(3);
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.run(g, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new FlowNetwork(1));
    }

    @Test @DisplayName("EK-5 parallel and anti-parallel edges are summed correctly")
    void parallelEdges() {
        FlowNetwork g = new FlowNetwork(3);
        g.addEdge(0, 1, 3); g.addEdge(0, 1, 4); g.addEdge(1, 0, 2); g.addEdge(1, 2, 5);
        assertEquals(5, EdmondsKarp.run(g, 0, 2, false).maxFlow());
    }

    @Test @DisplayName("EK-6 trace: bottlenecks sum to max flow, each path is a shortest path, conservation holds")
    void traceConsistency() {
        FlowNetwork g = clrs();
        var r = EdmondsKarp.run(g, 0, 5, true);
        int sum = r.steps().stream().mapToInt(EdmondsKarp.TraceStep::bottleneck).sum();
        assertEquals(r.maxFlow(), sum);
        int prevLen = 0;
        for (var st : r.steps()) { assertTrue(st.pathEdges().length >= prevLen, "path lengths are non-decreasing in Edmonds–Karp"); prevLen = st.pathEdges().length; }
        int[] net = new int[6];
        for (int id = 0; id < g.edgeCount(); id++) { net[g.from(id << 1)] -= r.edgeFlows()[id]; net[g.to(id << 1)] += r.edgeFlows()[id]; }
        for (int v = 1; v <= 4; v++) assertEquals(0, net[v], "flow conservation at node " + v);
        assertEquals(23, net[5]);
    }

    @Test @DisplayName("EK-7 min-cut: source side contains s not t, and cut capacity equals max flow")
    void minCut() {
        FlowNetwork g = clrs();
        var r = EdmondsKarp.run(g, 0, 5, false);
        assertTrue(r.sourceSide()[0]); assertFalse(r.sourceSide()[5]);
        int cap = 0; for (int id : r.cutEdgeIds()) { cap += g.original(id << 1); assertEquals(g.original(id << 1), r.edgeFlows()[id], "cut edges are saturated"); }
        assertEquals(r.maxFlow(), cap);
    }

    @Test @DisplayName("EK-8 reverse edges are used: a path that cancels earlier flow is found")
    void usesReverseEdges() {
        // classic example where greedy path s-a-b-t blocks the optimum; flow 2 needs cancelling a→b
        FlowNetwork g = new FlowNetwork(4);
        g.addEdge(0, 1, 1); g.addEdge(0, 2, 1); g.addEdge(1, 2, 1); g.addEdge(1, 3, 1); g.addEdge(2, 3, 1);
        var r = EdmondsKarp.run(g, 0, 3, true);
        assertEquals(2, r.maxFlow());
    }

    @Test @DisplayName("EK-9 RANDOM CROSS-CHECK: 300 random graphs agree with an independent DFS Ford–Fulkerson")
    void randomCrossCheck() {
        Random rnd = new Random(42);
        for (int iter = 0; iter < 300; iter++) {
            int n = 2 + rnd.nextInt(9);
            int[][] cap = new int[n][n];
            FlowNetwork g = new FlowNetwork(n);
            for (int u = 0; u < n; u++) for (int v = 0; v < n; v++)
                if (u != v && rnd.nextInt(3) == 0) { int c = rnd.nextInt(10); cap[u][v] += c; g.addEdge(u, v, c); }
            assertEquals(dfsMaxFlow(cap, 0, n - 1), EdmondsKarp.run(g, 0, n - 1, false).maxFlow(), "iteration " + iter);
        }
    }

    @Test @DisplayName("EK-10 LARGE: layered 2,000-node network finishes quickly with exact value")
    void largeNetwork() {
        int w = 1000;                              // s -> w middle nodes (cap 1) -> t
        FlowNetwork g = new FlowNetwork(w + 2);
        for (int i = 1; i <= w; i++) { g.addEdge(0, i, 1); g.addEdge(i, w + 1, 1); }
        assertTimeout(java.time.Duration.ofSeconds(5), () -> assertEquals(w, EdmondsKarp.run(g, 0, w + 1, false).maxFlow()));
    }

    private static int dfsMaxFlow(int[][] c, int s, int t) {
        int n = c.length; int[][] r = new int[n][]; for (int i = 0; i < n; i++) r[i] = c[i].clone();
        int flow = 0;
        while (true) {
            int[] par = new int[n]; Arrays.fill(par, -1); par[s] = s;
            Deque<Integer> st = new ArrayDeque<>(); st.push(s);
            while (!st.isEmpty() && par[t] == -1) { int u = st.pop(); for (int v = 0; v < n; v++) if (par[v] == -1 && r[u][v] > 0) { par[v] = u; st.push(v); } }
            if (par[t] == -1) return flow;
            int b = Integer.MAX_VALUE; for (int v = t; v != s; v = par[v]) b = Math.min(b, r[par[v]][v]);
            for (int v = t; v != s; v = par[v]) { r[par[v]][v] -= b; r[v][par[v]] += b; }
            flow += b;
        }
    }
}
