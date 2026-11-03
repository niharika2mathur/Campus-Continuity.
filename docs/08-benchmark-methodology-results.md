# 8. Benchmark Methodology and Results

## Methodology
- **Data:** `DatasetGenerator.generate(teams, seed)` — seeded `java.util.Random`; labs = max(3, ⌈T/30⌉), 10 slots (90/120/150 min), lab capacity 2–4, 2–5 equipment tags from 8; teams need 0–2 tags, size 2–5, 60/90/120 min, 50 % have 2–5 eligible slots. Same seed + size ⇒ identical input.
- **Procedure (`BenchmarkRunner`):** one warm-up run, then *R* repetitions; **median** reported. Edmonds–Karp measured both end-to-end (network build + flow + explanations) and flow-only; greedy first-fit in input order, plus the worst of four orderings. Bloom vs exact: *n* present keys inserted; probe list = *n* present + *n* absent keys (shuffled); times for Bloom only, `HashSet` only, and Bloom-then-verify; observed false-positive rate on the absent keys; false negatives on the present keys.
- **Reproduce:** UI *Performance & benchmarks*, or `POST /api/benchmarks {"sizes":[100,500,1000,2000],"repetitions":3,"seed":42,"falsePositiveProbability":0.01}`; or run `BenchmarkRunnerTest`.
- **Environment of the figures below:** containerised, **1 vCPU**, OpenJDK 21, seed 42, 3 repetitions. Your absolute times will differ; compare ratios and trends.
- **Threats to validity:** single seed; JIT warm-up (small sizes noisy); `HashSet` here is in memory — a database lookup would be orders of magnitude slower than either; synthetic, not real campus data.

## Results — allocation (measured)
| Teams | Graph V | Graph E | EK placed | Greedy (input) | Worst greedy order | EK total ms | EK flow ms | Greedy ms | BFS runs | Edge scans | Augmentations |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 100 | 142 | 1,320 | 81 | 73 | 73 | 7.7 | 2.8 | 0.36 | 82 | 89,679 | 81 |
| 500 | 672 | 21,511 | 466 | 418 | 417 | 93.3 | 69.3 | 0.56 | 467 | 8,074,334 | 466 |
| 1,000 | 1,342 | 81,791 | 969 | 920 | 920 | 451.9 | 413.4 | 1.06 | 970 | 59,212,097 | 969 |
| 2,000 | 2,672 | 304,065 | 1,911 | 1,776 | 1,776 | 4,484.5 | 4,338.2 | 10.74 | 1,912 | 468,456,815 | 1,911 |

**Reading it.** Edmonds–Karp places 8 / 48 / 49 / 135 more teams than input-order greedy (it is provably optimal for cardinality) but costs 21× / 167× / 426× / 417× the time. Augmentations equal the placed-team count (each adds 1 unit). Greedy is not "bad" — it is fast — but its quality depends on order and has no guarantee. The demo dataset (100 teams, seed 11): EK **92**, greedy input **77**, reversed 83, largest-first 81, most-constrained-first 85.

## Results — Bloom filter vs exact lookup (measured, p = 1 %)
| Keys | m bits | k | Bloom ms | HashSet ms | Bloom+verify ms | False positives (absent probes) | Observed FPR | False negatives |
|---|---|---|---|---|---|---|---|---|
| 100 | 959 | 7 | 0.10 | 0.03 | 0.10 | 0 / 100 | 0.00 % | 0 |
| 500 | 4,793 | 7 | 0.27 | 0.07 | 0.26 | 5 / 500 | 1.00 % | 0 |
| 1,000 | 9,586 | 7 | 0.64 | 0.12 | 0.69 | 4 / 1,000 | 0.40 % | 0 |
| 2,000 | 19,171 | 7 | 0.46 | 0.16 | 0.44 | 13 / 2,000 | 0.65 % | 0 |

**Reading it.** The Bloom filter was **slower** than an in-memory `HashSet` at every size (k = 7 hash computations per query vs one). This is the expected, honest result: it is *not* a speed-up over exact in-memory lookup. Its value is ≈ 9.6 bits per item of memory and, in this application, avoiding an authoritative database round-trip for every *definitely new* identifier. Observed FPR (0–1 %) is consistent with the 1 % target; false negatives are 0, as guaranteed.

## Theory vs empirical
Theory: EK O(V·E²), greedy O(T·S), Bloom O(k). Empirical growth is summarised in `06-complexity-analysis.md`; the two must not be conflated.

## Limits
Sizes are capped at 3,000 teams in the API; at 5,000 teams a single Edmonds–Karp run exceeded minutes in testing (AL-9 was therefore reduced to 1,000 teams). Dinic/Hopcroft–Karp would scale better but are outside the chosen syllabus scope.
