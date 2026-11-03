#!/usr/bin/env bash
# End-to-end API smoke test. Requires: running backend on :8080 with an EMPTY or demo database, curl, python3 (JSON pretty-print only).
# Usage: bash docs/api-smoke-test.sh [base-url]
set -euo pipefail
B="${1:-http://localhost:8080/api}"
j() { python3 -m json.tool; }
step() { printf '\n=== %s ===\n' "$1"; }

step "1. Reset and seed the sample campus (goes through validation + Bloom screening)"
curl -sf -X POST "$B/admin/seed?reset=true" | j | head -30

step "2. Dashboard (live Edmonds-Karp)"
curl -sf "$B/dashboard" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({k:d[k] for k in ("teams","allocated","unallocated","utilization")})'

step "3. Duplicate request must be rejected (409) after exact verification"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$B/teams" -H 'Content-Type: application/json' \
  -d '{"requestId":"REQ-2026-0001","code":"DUP-1","name":"dup","size":3,"requirements":[],"durationMinutes":60,"eligibleSlots":[]}'

step "4. Validation error (negative size -> 400 with field errors)"
curl -s -X POST "$B/teams" -H 'Content-Type: application/json' -d '{"requestId":"X1","code":"X1","size":-1,"durationMinutes":60}' | j

step "5. Allocation run + greedy comparison"
curl -sf -X POST "$B/allocations" -H 'Content-Type: application/json' -d '{"scenarioName":"smoke","includeTrace":false}' \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);o=d["outcome"];print("EK",o["allocated"],"/",o["totalTeams"],"greedy(INPUT)",d["greedy"][0]["allocated"])'

step "6. Observatory trace (greedy-trap preset must show max flow 2 and a reverse arc)"
curl -sf -X POST "$B/observatory/trace" -H 'Content-Type: application/json' -d '{"source":"PRESET:greedy-trap"}' \
  | python3 -c 'import sys,json;o=json.load(sys.stdin)["outcome"];print("maxFlow",o["terminal"]["maxFlow"],"steps",len(o["steps"]),"reverse used",any(any(s["pathReverse"]) for s in o["steps"]))'

step "7. What-if: disable LAB-AI1 (re-runs the algorithm for baseline and scenario)"
curl -sf -X POST "$B/simulations" -H 'Content-Type: application/json' \
  -d '{"name":"AI lab outage","mutations":[{"type":"DISABLE_LAB","target":"LAB-AI1","reason":"smoke"}]}' \
  | python3 -c 'import sys,json;r=json.load(sys.stdin)["result"];print(r["before"]["allocated"],"->",r["after"]["allocated"],"displaced",len(r["diff"]["newlyDisplaced"]))'

step "8. Bloom live state"
curl -sf "$B/bloom/live" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({k:d[k] for k in ("bits","hashes","insertions","occupancy")},d["stats"])'

step "9. Invalid CSV preview (negative sizes, duplicates, bad numbers)"
curl -sf -F "file=@sample-data/teams_with_errors.csv" "$B/import/teams/preview" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({k:d[k] for k in ("valid","invalid","duplicate")})'

step "10. Malformed CSV"
curl -sf -F "file=@sample-data/malformed.csv" "$B/import/teams/preview" | j

step "11. Benchmark (3 sizes)"
curl -sf -X POST "$B/benchmarks" -H 'Content-Type: application/json' -d '{"sizes":[100,300,600],"repetitions":3,"seed":42,"falsePositiveProbability":0.01}' \
  | python3 -c 'import sys,json;[print(r["teams"],r["ekAllocated"],r["greedyAllocated"],round(r["ekTotalMs"],1)) for r in json.load(sys.stdin)["report"]["allocation"]]'
echo; echo "Smoke test finished."
