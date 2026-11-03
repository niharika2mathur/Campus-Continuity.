# Campus Continuity
**Intelligent Campus Resource Recovery and Allocation Platform** — CIA 3 Advanced Algorithm project (2026–27).

When laboratories, equipment or time slots become unavailable, administrators must re-home student teams without exceeding capacity. This application computes the **maximum feasible number of allocations** (Edmonds–Karp max-flow / min-cut), **screens duplicate requests** (Bloom filter + exact verification), and lets administrators **simulate disruptions** (what-if) with before/after comparison.

| | |
|---|---|
| Core algorithms (own Java code) | Edmonds–Karp maximum flow + minimum cut · Bloom filter (CO5) · greedy baseline |
| Backend | Java 21, Spring Boot 3.3, Maven, Spring Data JPA, Flyway, PostgreSQL 16, JUnit 5 |
| Frontend | React 18, Vite, Tailwind CSS, Recharts, Lucide, custom SVG/Canvas visualisations, Vitest |
| Objective | **Maximum number of placed teams.** Preferences and travel distance are *not* optimised. |

## Repository layout
```
backend/   pom.xml, src/main (engine/ sim/ csv/ bench/ = pure-Java algorithms; web/ service/ repo/ domain/ dto/ = Spring), src/test, tools/TraceFixture.java
frontend/  Vite app (src/pages = 12 pages, src/components, src/lib, tests)
docs/      01 proposal … 11 viva Q&A, api-smoke-test.sh
sample-data/ labs.csv slots.csv teams.csv (+ error/edge demo files, generate_sample_data.py)
docker-compose.yml  .env.example  .gitignore
```

## Prerequisites
JDK 21, Maven 3.9+, Node 20+ (22 tested), Docker (for PostgreSQL), `curl` (optional smoke test).

## Run locally (exact steps)
```bash
# 0. configuration (optional; defaults already match docker-compose)
cp .env.example .env

# 1. database
docker compose up -d db

# 2. backend  (http://localhost:8080) — Flyway creates the schema on start
cd backend
mvn test                 # 56 JUnit 5 tests
mvn spring-boot:run

# 3. frontend (http://localhost:5173, proxies /api to :8080)
cd ../frontend
npm install
npm test                 # 17 Vitest tests
npm run dev
```
Open `http://localhost:5173` → *Continue as administrator* → **Overview → Load sample data**.

**Everything in containers:** `docker compose --profile full up --build` → app on `http://localhost:3000`.

## Try it
```bash
bash docs/api-smoke-test.sh          # scripted end-to-end API walkthrough (backend + sample data)
```
Suggested demo: see `docs/10-demo-script-8-minutes.md`. Error-handling files: `sample-data/teams_with_errors.csv`, `labs_invalid.csv`, `malformed.csv`. Greedy-trap instance: `sample-data/greedy_trap_{labs,slots,teams}.csv` (import in that order).

## CSV formats
`labs.csv`: `code,name,building,capacity,max_team_size,equipment,status,reason` · `slots.csv`: `code,label,day,start,duration_minutes,status` · `teams.csv`: `request_id,code,name,size,requirements,duration_minutes,eligible_slots` (lists separated by `;`). Import order: labs → slots → teams.

## API example
```bash
curl -X POST localhost:8080/api/allocations -H 'Content-Type: application/json' -d '{"scenarioName":"demo","includeTrace":false}'
```
Full reference: `docs/04-api-documentation.md`.

## Verification status — read this
| Check | Status |
|---|---|
| 56 backend JUnit 5 tests (algorithms, simulator, CSV, benchmarks, presets) | ✅ **Executed** with the real JUnit Platform Console launcher against `engine/ sim/ csv/ bench/` compiled by `javac` (JDK 21): 56 passed, 0 failed |
| Edmonds–Karp vs known graph (CLRS = 23), 300 random graphs vs independent Ford–Fulkerson, 150 allocation instances vs exhaustive search | ✅ part of the 56 |
| Bloom: no false negatives, FPR within target | ✅ part of the 56 |
| Benchmarks (4 sizes) and greedy-vs-EK comparison | ✅ **Executed**; results in `docs/08-…` |
| Frontend `npm test` (17) and `npm run build` | ✅ **Executed** |
| Frontend rendering of real engine traces | ✅ tested with traces serialised from the Java engine |
| **Spring Boot layer** (controllers, services, JPA, Flyway, CORS), `mvn test`, `mvn package` | ⚠️ **Written but NOT compiled or run** — the authoring sandbox had no Maven Central access. A syntax-level parse found no syntax errors, but type/wiring errors are possible. Expect to fix small issues on first `mvn test` |
| PostgreSQL migration, Docker images, `docker compose`, `docs/api-smoke-test.sh` | ⚠️ **Not executed** (no Docker/PostgreSQL in sandbox) |
| Frontend ⇄ backend integration over HTTP | ⚠️ **Not executed** (needs the Spring layer running) |
| Screenshots | ❌ none captured (requires the running stack) |

If anything fails on your machine, the algorithm modules are independent of Spring: `javac` + JUnit can run them directly (see `docs/07-testing.md`).

## Known limitations
Demo login only (no authentication); Edmonds–Karp slows sharply beyond ~2,000 teams on dense data (benchmark sizes capped at 3,000); Bloom filters cannot delete, so removed request IDs become exact-checked false positives; the Observatory trace is limited to 400 nodes.

## Documentation index
`docs/01-project-proposal.md` · `02-technical-architecture.md` · `03-database-schema.md` · `04-api-documentation.md` · `05-algorithms-pseudocode-correctness.md` · `06-complexity-analysis.md` · `07-testing.md` · `08-benchmark-methodology-results.md` · `09-contribution-template.md` · `10-demo-script-8-minutes.md` · `11-viva-questions-answers.md`

## Attribution
Datasets are synthetic (seeded generators). Edmonds–Karp/CLRS test network from *Introduction to Algorithms*. Libraries provide infrastructure only; no graph or Bloom-filter library is used.
