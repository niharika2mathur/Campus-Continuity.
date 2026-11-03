# 4. API Documentation (base path `/api`, JSON)

**Errors** are uniform: `{timestamp, status, error, message, fieldErrors?, path}`. 400 validation/malformed input, 404 unknown id, 409 duplicate (code/request id), 413 file > 2 MB, 500 unexpected.

| Method & path | Purpose | Notes |
|---|---|---|
| `GET/POST /labs`, `GET/PUT /labs/{id}`, `PATCH /labs/{id}/status` | Laboratories | status body `{status, reason}`; capacity `0..100000` |
| `GET/POST /slots`, `PUT /slots/{id}`, `PATCH /slots/{id}/status` | Time slots | |
| `GET/POST /teams`, `GET/PUT/DELETE /teams/{id}` | Teams | `POST ?strict=true` rejects requests no lab could ever host. Response `{team, screening{bloomResult, exactResult, outcome}, warnings[]}` |
| `GET /requests` | Screening log (latest 300) | |
| `POST /requests/screen` `{requestId}` | Read-only Bloom + exact probe | returns hash positions, bits, classification |
| `POST /import/{labs\|slots\|teams}/preview` (multipart `file`) | Validate only | rows: VALID / INVALID / DUPLICATE with messages |
| `POST /import/{type}/commit` | Import valid rows | team rows pass through Bloom screening |
| `GET /import/{type}/columns` | Column spec | |
| `POST /allocations` `{scenarioName, includeTrace}` | Run Edmonds–Karp on current DB | returns outcome + greedy comparison; `includeTrace` ≤ 400 nodes |
| `GET /allocations`, `GET /allocations/{id}`, `GET /allocations/{id}/export` | History, stored result, CSV | CSV neutralises formula injection |
| `GET /observatory/presets`, `POST /observatory/trace` `{source, maxTeams}` | Trace for the visualiser | `source = LIVE` or `PRESET:<id>` |
| `GET /bloom/live` | Live filter state (bit string, stats) | |
| `GET /bloom/sandbox`, `POST /bloom/sandbox/{reset,add,query}` | Experiment filter | reset body `{expectedInsertions ≤ 5000, falsePositiveProbability}` |
| `POST /simulations` `{name, mutations[]}` | Re-run baseline + scenario | mutation types: `DISABLE_LAB, RESTORE_LAB, REDUCE_CAPACITY, INCREASE_CAPACITY, DISABLE_SLOT, RESTORE_SLOT, ADD_EQUIPMENT, REMOVE_EQUIPMENT, ADD_TEAMS` |
| `GET /simulations`, `GET /simulations/{id}`, `POST /simulations/apply` | History / commit scenario | apply changes live data |
| `POST /benchmarks` `{sizes[1..8, each ≤3000], repetitions 1..15, seed, falsePositiveProbability}` | Run benchmarks | `GET /benchmarks[/{id}]` history |
| `GET /dashboard` | Live KPIs | runs EK on every call |
| `GET /reports/activity`, `GET /reports/runs?kind=` | Reports | |
| `POST /admin/seed?reset=true`, `POST /admin/reset`, `GET /admin/health` | Data management | |

## Examples
```bash
curl -X POST localhost:8080/api/admin/seed?reset=true
curl -X POST localhost:8080/api/allocations -H 'Content-Type: application/json' -d '{"scenarioName":"demo","includeTrace":false}'
curl -X POST localhost:8080/api/simulations -H 'Content-Type: application/json' \
  -d '{"name":"AI lab outage","mutations":[{"type":"DISABLE_LAB","target":"LAB-AI1","reason":"cooling failure"}]}'
curl -F file=@sample-data/teams_with_errors.csv localhost:8080/api/import/teams/preview
```
A scripted walkthrough is in `docs/api-smoke-test.sh`.
