# 2. Technical Architecture

```
React (Vite, Tailwind, Recharts, SVG/Canvas)  ──REST/JSON──▶  Spring Boot controllers (validation, error mapping)
                                                                      │
                                                          Services (transactions, persistence glue)
                                                                      │
                         ┌────────────────────────────────────────────┼───────────────────────────────┐
                         ▼                                            ▼                               ▼
              Pure-Java engine packages                       Spring Data JPA repos            Flyway migration
   engine/ (FlowNetwork, EdmondsKarp, BloomFilter,             ▼
   AllocationEngine, GreedyAllocator, Compat, Model)       PostgreSQL 16
   sim/ (ScenarioSimulator, PresetInstances)
   csv/ (CsvParser, ImportValidator)
   bench/ (DatasetGenerator, BenchmarkRunner)
```

## Separation of concerns
| Layer | Package | Responsibility |
|---|---|---|
| API | `web` | HTTP mapping, `@Valid`, `ApiExceptions` → uniform JSON errors |
| Service | `service` | Transactions, DB⇄engine conversion (`Mappers`), Bloom screening, run history |
| Algorithms | `engine`, `sim`, `csv`, `bench` | **No Spring/JPA imports.** Self-implemented algorithms and pure logic; unit-tested without a container |
| Persistence | `domain`, `repo`, `db/migration` | Entities, Spring Data interfaces (infrastructure only), schema |
| DTOs | `dto` | Request/response records with Bean Validation |

## Key data flows
1. **Allocation run:** `AllocationService` → `CatalogService.snapshot()` (fresh DB read) → `AllocationEngine.solve` (build network → `EdmondsKarp.run`) → `GreedyAllocator` for comparison → result persisted as JSON in `run_record` → returned.
2. **Request screening:** `TeamService.register` → `BloomScreeningService.screen` → if *definitely absent*: insert; if *possible*: `TeamRepo.existsByRequestId` (authoritative) → duplicate ⇒ 409, else false positive ⇒ insert. Every event is written to `request_log`.
3. **What-if:** `SimulationService` → `ScenarioSimulator.run(snapshot, mutations)` applies changes to an immutable *copy*, re-solves baseline **and** scenario. Nothing is cached; reset = discard. `apply` commits to the DB only on explicit confirmation.
4. **Observatory:** `ObservatoryService` → `AllocationEngine.solve(snapshot, trace=true)`; the trace (BFS visit order, path, bottleneck, flows per step, final reachable set, cut edges) is serialised; the browser only *replays* it (`frameState`, `FlowGraph`).

## Design decisions
- **Engine isolated from frameworks** so the assessed code is visibly original and testable (frameworks supply infrastructure only, per the guidelines).
- **Snapshot-per-request** instead of caching → no stale results after inputs change.
- **Trace limit** (400 nodes) keeps the visualisation readable and responses small; non-trace runs scale to thousands of teams.
- **Bloom rebuild on startup** from the `team` table (authoritative). Filters cannot delete, so deleted request IDs stay in the filter and are corrected by the exact check (documented false-positive source).
- **JPA entities with public fields** keep the persistence layer compact; they never reach the engine.
- **No authentication** (demo gate in `localStorage`) — out of the assessed scope; documented as a limitation.

## Configuration
`application.yml` reads `DB_URL, DB_USER, DB_PASSWORD, SERVER_PORT, CORS_ALLOWED_ORIGINS, BLOOM_EXPECTED_INSERTIONS, BLOOM_FPP`. See `.env.example`.
