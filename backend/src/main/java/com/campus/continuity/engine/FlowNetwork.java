package com.campus.continuity.engine;

import java.util.Arrays;

/**
 * Directed residual graph stored as a forward-star (adjacency array). Every added edge u→v creates a pair of
 * arcs: arc 2i (forward, capacity c) and arc 2i+1 (reverse, capacity 0); the partner of arc e is e^1.
 * {@code cap[e]} holds the CURRENT residual capacity; {@code orig[e]} the original capacity.
 */
public final class FlowNetwork {
    private final int n;
    private final int[] head, tail;
    private int[] next, to, from, cap, orig;
    private int m; // number of arcs (2 per edge)

    public FlowNetwork(int nodes) {
        if (nodes < 2) throw new IllegalArgumentException("A flow network needs at least a source and a sink");
        n = nodes;
        head = new int[n]; tail = new int[n];
        Arrays.fill(head, -1); Arrays.fill(tail, -1);
        next = new int[32]; to = new int[32]; from = new int[32]; cap = new int[32]; orig = new int[32];
    }

    /** @return edge id (index among forward edges). */
    public int addEdge(int u, int v, int capacity) {
        if (u < 0 || u >= n || v < 0 || v >= n) throw new IllegalArgumentException("Edge endpoint out of range: " + u + "→" + v);
        if (u == v) throw new IllegalArgumentException("Self-loops are not allowed: " + u);
        if (capacity < 0) throw new IllegalArgumentException("Negative capacity on edge " + u + "→" + v + ": " + capacity);
        ensure(m + 2);
        int id = m >> 1;
        link(u, v, capacity); link(v, u, 0);
        return id;
    }

    private void ensure(int need) {
        if (need <= to.length) return;
        int len = Math.max(need, to.length * 2);
        next = Arrays.copyOf(next, len); to = Arrays.copyOf(to, len); from = Arrays.copyOf(from, len);
        cap = Arrays.copyOf(cap, len); orig = Arrays.copyOf(orig, len);
    }

    private void link(int u, int v, int c) {
        to[m] = v; from[m] = u; cap[m] = c; orig[m] = c; next[m] = -1;
        if (head[u] == -1) head[u] = tail[u] = m; else { next[tail[u]] = m; tail[u] = m; }
        m++;
    }

    public int nodeCount() { return n; }
    public int edgeCount() { return m >> 1; }
    public int first(int u) { return head[u]; }
    public int next(int e) { return next[e]; }
    public int to(int e) { return to[e]; }
    public int from(int e) { return from[e]; }
    public int residual(int e) { return cap[e]; }
    public int original(int e) { return orig[e]; }

    /** Push {@code b} units along arc e: shrink its residual, grow its reverse partner. */
    public void push(int e, int b) { cap[e] -= b; cap[e ^ 1] += b; }

    /** Flow on forward edge id = original capacity minus remaining residual capacity. */
    public int flowOf(int edgeId) { return orig[edgeId << 1] - cap[edgeId << 1]; }

    public int[] flows() {
        int[] f = new int[edgeCount()];
        for (int i = 0; i < f.length; i++) f[i] = flowOf(i);
        return f;
    }
}
