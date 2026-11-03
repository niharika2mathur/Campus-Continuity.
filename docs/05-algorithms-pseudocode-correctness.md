# 5. Algorithms — Pseudocode and Correctness

## 5.1 Allocation model → flow network
Nodes: source `s`, one node per team, one node per usable (laboratory, slot) pair, sink `t`.
Edges: `s→team` cap 1; `team→slot` cap 1 **iff** compatible; `slot→t` cap = laboratory capacity.
Compatibility (`Compat.check`): lab AVAILABLE ∧ slot AVAILABLE ∧ capacity > 0 ∧ lab.equipment ⊇ team.requirements ∧ team.size ≤ lab.maxTeamSize ∧ team.duration ≤ slot.duration ∧ (team.eligibleSlots = ∅ ∨ slot ∈ team.eligibleSlots).

**Claim.** The integral maximum flow value equals the maximum number of teams that can be placed without exceeding any slot capacity.
*Proof sketch.* A feasible placement sends one unit along `s→team→slot→t` per placed team; capacities enforce ≤ 1 slot per team and ≤ capacity teams per slot. Conversely, by flow integrality an integral max flow decomposes into such unit paths. ∎ The objective is **cardinality only**; no preference or distance is optimised.

## 5.2 Edmonds–Karp (`EdmondsKarp.run`)
```
flow ← 0
repeat
    BFS from s over arcs with residual > 0, record parentArc[v]
    if t unreachable: S ← reached set; stop
    b ← min residual along the s→t path
    for each arc e on the path: res[e] −= b ; res[e xor 1] += b      # reverse arc
    flow ← flow + b
return flow, minimum cut (S, V∖S)
```
Residual graph = forward-star arrays; arc `2i` forward, `2i+1` reverse.

**Correctness.**
1. *Invariants:* the push keeps `0 ≤ flow ≤ cap` (b ≤ every residual on the path) and conservation at inner nodes (flow enters and leaves each path node equally).
2. *Termination / bound:* BFS paths are shortest; the distance from `s` to any node never decreases, and each augmentation saturates a *critical* edge that can become critical again only after its distance grows, so ≤ O(V·E) augmentations. Here every augmentation adds ≥ 1 and flow ≤ #teams, so ≤ #teams augmentations.
3. *Optimality (max-flow min-cut):* when BFS cannot reach `t`, let `S` be the reached set. Every arc leaving `S` has residual 0 ⇒ forward edges are saturated and backward edges carry 0 flow, so flow = capacity(S, V∖S). No flow can exceed any cut, hence the flow is maximum and (S, V∖S) is a **minimum cut**. Tests EK-6/EK-7 assert both the saturation and the value equality on every run.
4. *Reverse arcs* let a later path cancel an earlier choice (see the "greedy trap" preset where one path uses a reverse arc).

**Bottleneck explanation** (`AllocationEngine.bottleneck`): slot nodes in `S` are fully used; teams in `S` can reach only those nodes. With `c` the capacity of `S`'s slot nodes and `n` the capacity-competing teams in `S`, at least `n − c` teams cannot be placed. Teams with *no* compatible slot are reported separately (`NO_COMPATIBLE_SLOT`) with the dominant blocking reasons.

## 5.3 Greedy baseline (`GreedyAllocator`)
```
order teams (INPUT | REVERSED | LARGEST_FIRST | MOST_CONSTRAINED_FIRST)
for each team: take the first compatible slot node with remaining capacity; never revisit
```
*Not optimal:* with one capacity-1 lab, slots S1,S2, team FLEX (S1 or S2) listed before RIGID (S1 only): greedy gives FLEX→S1, RIGID unplaced (1 team); optimum places both (test AL-5). Results depend on ordering (test GR-2).

## 5.4 Bloom filter (`BloomFilter`) — CO5
```
m = ⌈ −n·ln p / (ln 2)² ⌉ ; k = round((m/n)·ln 2)
positions(x): h1 = FNV-1a64(x) ; h2 = splitmix64(FNV-1a64'(x)) | 1 ; pos_i = (h1 + i·h2) mod m , i = 0..k−1     # double hashing
add(x): set bits pos_0..pos_{k−1}
mightContain(x): all k bits set?
```
**Properties.** *No false negatives:* bits are only ever set, never cleared, so every inserted item's k bits stay 1 (BF-1, BF-8). *False positives:* after n insertions P(bit = 0) = (1 − 1/m)^{kn} ≈ e^{−kn/m}, so FPP ≈ (1 − e^{−kn/m})^k, minimised at k = (m/n) ln 2 where FPP ≈ p. `theoreticalFpp()` implements this; `estimatedFpp()` uses the measured fill ratio^k.

**Application use (never "definitely present").** Definitely absent ⇒ accept without lookup. Possibly present ⇒ exact lookup in the `team` table; PRESENT ⇒ confirmed duplicate (409); ABSENT ⇒ **false positive** counted, request accepted. Reported statistics: insertions, queries, definite negatives, possible positives, confirmed duplicates, false positives, FPR = FP / (FP + definite negatives).

## 5.5 What-if simulator
`apply` transforms an immutable `Snapshot` (labs, slots, teams) per mutation; `run` solves baseline and scenario with the same engine and computes: allocated/unallocated deltas, utilisation delta, *displaced* (placed before, not after), *recovered* (the reverse), *moved* teams, and bottleneck node sets appeared/persisting/resolved.
