# 10. Eight-Minute Demonstration Script

**Before starting:** `docker compose up -d db`, start backend and frontend, open `http://localhost:5173`, click *Continue as administrator*. Database empty.

| Time | Do | Say / show |
|---|---|---|
| 0:00–0:40 | Overview → **Load sample data** | Problem: disruptions leave placeable teams unplaced. Two algorithms: Edmonds–Karp (Unit 5) and Bloom filter (CO5), both written from scratch in Java; the browser only displays results. |
| 0:40–1:30 | Dashboard | 100 requests, ~92 allocated, utilisation; unallocated explained by the min-cut lines. "Computed live by Java on every load." |
| 1:30–2:45 | **Allocation studio** → import `teams_with_errors.csv` → *Validate* | Error handling: negative size, non-numeric, duplicate id in file, existing id, missing id → INVALID/DUPLICATE with messages. Also `malformed.csv`. Then *Run Edmonds–Karp*; open *Baseline comparison*: EK 92 vs greedy 77. Export CSV. |
| 2:45–4:30 | **Algorithm observatory** → preset *Greedy trap* → Execute → Play | Show BFS search, augmenting path, bottleneck, flow update. Step 2 uses an **amber reverse arc**: FLEX is re-routed. Final frame: min-cut in red; "max-flow = min-cut". Pseudocode highlights follow the step. "Every frame is the Java trace." |
| 4:30–5:30 | **Bloom intelligence** | Live filter: bits, occupancy, stats. Probe `REQ-2026-0001` → *confirmed duplicate* (positive + exact DB check). Probe a new id → *definitely absent*. Sandbox: Rebuild (50, 5 %), *Insert 40 random*, query unseen ids until a **false positive** appears; stress: overfill → FPR up, still no false negatives. |
| 5:30–7:00 | **What-if simulator** | Disable `LAB-AI1` (reason "cooling failure") → Run: allocated drops, displaced teams listed, bottleneck appears. Add *Increase capacity* on `LAB-GEN1` → recovered teams. *Reset*. Save two scenarios → *Compare*. "Both baseline and scenario are re-run by the algorithm each time." |
| 7:00–7:45 | **Benchmarks** | Run 100/500/1000. Show chart: EK optimal but slower; Bloom *slower* than HashSet in memory — honest result; theory vs empirical box. |
| 7:45–8:00 | Wrap up | Tests: 56 JUnit (incl. random cross-checks against brute force), 17 frontend; limits: cardinality objective only, demo login. |

**Failure-handling talking points:** zero capacity, disconnected network, incompatible team reasons, duplicate IDs, malformed CSV, negative capacities blocked in API + DB + engine.
**If asked to change input:** edit a lab capacity or upload `greedy_trap_*.csv` (labs → slots → teams) and re-run.
