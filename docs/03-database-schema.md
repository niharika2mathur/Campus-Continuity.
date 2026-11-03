# 3. Database Schema (Flyway `V1__schema.sql`)

| Table | Purpose | Key columns / constraints |
|---|---|---|
| `laboratory` | Lab inventory | `code` UNIQUE; `capacity ≥ 0`; `max_team_size ≥ 1`; `equipment` (comma-separated, lowercase); `status ∈ {AVAILABLE, DISRUPTED, DEACTIVATED}`; `reason` |
| `time_slot` | Bookable slots | `code` UNIQUE; `duration_minutes > 0`; `status`; `reason` |
| `team` | Team + its single request; **authoritative request store** | `request_id` UNIQUE (verifies Bloom positives); `code` UNIQUE; `size ≥ 1`; `requirements`; `duration_minutes > 0`; `eligible_slots` |
| `request_log` | Audit of every screening | `bloom_result` NEGATIVE/POSSIBLE; `exact_result` NOT_CHECKED/ABSENT/PRESENT; `outcome` ACCEPTED/REJECTED_DUPLICATE/REJECTED_INVALID; `source` |
| `run_record` | History of allocation/simulation/benchmark runs | `kind`, `summary`, `payload` (JSON text; trace omitted) |
| `activity_log` | Change history for reports | `action`, `detail`, `created_at` |

```
laboratory 1───* (implicit via snapshot)  time_slot        team ───1 request_log (by request_id/team_code, no FK: log must survive deletes)
```
Relations between labs, slots and teams are **computed** (compatibility rules), not stored; the flow network is rebuilt per run. Check constraints prevent negative capacities at the database level as well as in the API and the engine's `Model` constructors.

Reproduce: Flyway runs automatically on backend start; or `psql -f backend/src/main/resources/db/migration/V1__schema.sql`.
