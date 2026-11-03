# 11. Viva Preparation — Questions and Model Answers

1. **Why is allocation a max-flow problem?** Teams have supply 1, slots have capacity, edges encode compatibility; an integral max flow decomposes into unit paths = a feasible placement, so flow value = maximum teams placed (flow integrality).
2. **What does Edmonds–Karp add to Ford–Fulkerson?** BFS picks a shortest augmenting path, giving O(V·E²) regardless of capacities (plain FF with DFS can be pseudo-polynomial or non-terminating on irrational capacities).
3. **Why O(V·E) augmentations?** Each augmentation saturates a critical edge; shortest-path distances are non-decreasing, and an edge can become critical again only after its endpoint distance has grown by ≥ 2, i.e. ≤ V/2 times; E edges ⇒ O(V·E).
4. **What are reverse edges for?** They represent the ability to undo earlier flow; without them greedy-like blocking paths could stop short of the optimum (show "Greedy trap").
5. **How do you get the min cut?** After the final failing BFS, S = nodes reached with residual > 0; edges S→V∖S are saturated and sum to the flow (`EdmondsKarp.sourceSide`, `cutEdgeIds`).
6. **How do you explain unallocated teams?** Teams with no compatible slot (reasons from `Compat`) vs teams competing for saturated nodes in S (deficit = |teams| − capacity(S)).
7. **Does your flow optimise preferences/distance?** No — only the *number* of placed teams. That would need min-cost flow.
8. **Why does greedy fail and when is it acceptable?** Early commitments block later teams; its result depends on order. Acceptable when speed matters and a good ordering heuristic exists; here the gap was 15 teams on the demo data and up to 135 at 2,000 teams.
9. **How does your Bloom filter work?** Bit array of m bits, k positions per item via double hashing (h1 + i·h2) mod m; add sets bits; query checks all.
10. **Why no false negatives?** Bits are never cleared, so inserted items' bits stay set.
11. **How are m and k chosen? FPP formula?** m = −n ln p/(ln 2)², k = (m/n) ln 2; FPP ≈ (1 − e^{−kn/m})^k.
12. **What does a positive mean in your app?** "Possibly present": always verified with `existsByRequestId` in PostgreSQL; PRESENT ⇒ 409 duplicate, ABSENT ⇒ false positive counted and request accepted.
13. **Is the Bloom filter faster than a hash set?** Not in memory (measured 0.46 vs 0.16 ms at 2,000 keys). Benefit: compactness and skipping an expensive authoritative lookup for definite negatives.
14. **What happens when a team is deleted?** Its bits remain (no deletion); a later submission is a false positive that the exact check resolves. Counting Bloom filters would allow deletion.
15. **How do you prevent stale what-if results?** Snapshot read per call, immutable copy mutated, both baseline and scenario recomputed; nothing cached.
16. **How do you prove your implementation is correct?** Known graph (CLRS = 23), 300 random graphs vs an independent Ford–Fulkerson, 150 random allocation instances vs exhaustive search, min-cut = flow assertions, invariant tests on traces.
17. **Walk through a critical method.** `EdmondsKarp.run`: BFS with `parentArc`, bottleneck loop, `push(e,b)` updates `cap[e]` and `cap[e^1]`; terminal BFS defines S.
18. **Complexity vs measurements?** Worst-case O(V·E²); here augmentations ≤ T and E ∝ T² so ≈ T³; measured edge-scan growth ≈ T³.
19. **What are the limitations?** Cardinality objective, single seed benchmarks, demo login, EK too slow beyond a few thousand teams (use Dinic), trace limited to 400 nodes.
20. **Examiner changes the input — what do you do?** Upload a modified CSV (labs → slots → teams) or use the simulator; results are recomputed by the backend.
