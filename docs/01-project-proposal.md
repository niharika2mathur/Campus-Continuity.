# 1. Project Proposal — Campus Continuity (CIA 3, Advanced Algorithm, 2026–27)

| | |
|---|---|
| **Title** | Campus Continuity — Intelligent Campus Resource Recovery and Allocation Platform |
| **Team** | *(fill in: names and IDs of 2–3 students)* |
| **Faculty approval** | *(fill in)* |

## Real-world problem
When a laboratory, classroom, equipment set or time slot becomes unavailable (power failure, equipment fault, maintenance), administrators must quickly re-home student project teams to compatible resources without exceeding capacity. Doing this by hand is slow and tends to leave placeable teams unplaced.

## Target user
Academic operations / laboratory administrators who need to (a) see the maximum number of teams that can be accommodated, (b) understand *why* others cannot, and (c) test "what if" disruptions before acting.

## Proposed features
Resource inventory with disruption tracking; team/request management with duplicate screening; CSV import with validation; maximum-flow allocation; algorithm observatory (step-by-step trace); Bloom-filter intelligence; what-if simulator with before/after comparison; benchmarking against baselines; reports and exports.

## Two core techniques (both implemented from scratch in Java)
| Technique | Syllabus unit | Why appropriate |
|---|---|---|
| **Edmonds–Karp maximum flow / min-cut** | Unit 5 (network flow) | Team→slot assignment with capacities is a bipartite *b-matching*, solvable exactly as max-flow; the min cut explains bottlenecks. |
| **Bloom filter** (**CO5**, probabilistic data structure) | Unit 6 | Incoming request identifiers arrive from many CSV sources; a Bloom filter cheaply identifies *definitely new* requests and flags *possible* duplicates for an exact database check. |

Baselines: first-fit greedy allocation (vs Edmonds–Karp) and exact hash-set / database lookup (vs Bloom filter).

## Data source
Realistic synthetic data: `sample-data/*.csv` (8 laboratories, 12 slots, 100 requests, produced by the documented, seeded `generate_sample_data.py`) and a seeded benchmark generator (`DatasetGenerator`) up to 3,000 teams.

## Measurable success criteria
1. Edmonds–Karp placement equals an exhaustive optimum on 150 random small instances (test AL-8) and never trails greedy.
2. Zero Bloom false negatives; observed false-positive rate within the configured target (tests BF-1, BF-3, BM-1).
3. ≥ 5 documented correctness tests per core algorithm incl. boundary cases (56 backend tests).
4. Benchmarks at ≥ 3 increasing sizes with a baseline comparison, reproducible from a seed.
5. What-if scenario re-runs the algorithm and reports displaced/recovered teams and bottleneck changes.

## Scope limits (stated up front)
Objective is **maximum cardinality** — preferences and travel distance are not optimised. Bloom hits are "possibly present". Login is a demo gate, not authentication.
