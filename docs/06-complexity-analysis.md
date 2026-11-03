# 6. Time and Space Complexity

Notation: T teams, S usable lab-slot nodes, V = T + S + 2, E = O(T·S) compatibility edges + T + S.

| Component | Time | Space | Notes |
|---|---|---|---|
| Network construction | O(T·S) compat checks | O(V + E) | each check O(|requirements|) |
| **Edmonds–Karp** | **O(V·E²)** worst case | O(V + E) | O(V·E) augmentations × O(E) BFS. Because every augmentation adds 1 unit and max flow ≤ T, augmentations ≤ T, giving O(T·E) = O(T²·S) for this model |
| Trace recording (optional) | O(augmentations·E) | O(augmentations·E) | one flow snapshot per step; capped at 400 nodes |
| Min cut extraction | O(V + E) | O(V) | from the last BFS |
| Greedy first-fit | O(T·S) | O(T + S) | + O(T·S) for MOST_CONSTRAINED ordering |
| Bloom insert / query | O(k) | O(m) bits, m = −n ln p/(ln 2)² | ≈ 9.6 bits/item at p = 1% |
| Exact HashSet lookup | O(1) expected | O(n) entries | |
| CSV parse / validate | O(size) | O(rows) | |
| What-if run | 2 × EK + O(T) diff | O(V + E) | |

## Theory vs empirical (see `08-benchmark-methodology-results.md`)
Theory gives *worst-case upper bounds*. In the reference run, Edmonds–Karp flow time grew with log-log slopes ≈ 2.0 (100→500 teams), 2.6 (500→1000) and 3.4 (1000→2000), and edge scans grew ≈ T³. That matches the refined bound *augmentations ≤ T* times *E ∝ T²* (the generator adds labs proportionally to T, so compatibility edges grow quadratically): O(T·E) ≈ O(T³). Slopes at small sizes are dominated by JIT warm-up and cache effects. The numbers depend on machine and generator; they describe behaviour on *this* data family and are not a proof of complexity.
