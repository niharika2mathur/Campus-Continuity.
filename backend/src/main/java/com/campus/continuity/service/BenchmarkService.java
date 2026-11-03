package com.campus.continuity.service;

import com.campus.continuity.bench.BenchmarkRunner;
import com.campus.continuity.dto.Dtos.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class BenchmarkService {
    public record BenchmarkView(Long id, Instant createdAt, BenchmarkRunner.Report report, List<String> notes) {}

    private final RunStore runs; private final ActivityService activity;
    public BenchmarkService(RunStore runs, ActivityService activity) { this.runs = runs; this.activity = activity; }

    public BenchmarkView run(BenchmarkRequest r) {
        var report = BenchmarkRunner.run(r.sizes(), r.repetitions(), r.seed(), r.falsePositiveProbability());
        List<String> notes = List.of(
                "Times are medians of " + r.repetitions() + " repetitions after a warm-up, measured on the machine running the backend; they vary between machines.",
                "Theory: Edmonds–Karp O(V·E²) worst case; greedy O(T·S) compatibility checks; Bloom filter O(k) per operation; hash-set lookup O(1) expected.",
                "Empirical growth is data-dependent and need not follow the worst-case bound. A Bloom filter is not always faster than an exact in-memory hash lookup; its value is memory compactness and avoiding costly authoritative lookups (e.g. a database call) for definite negatives.",
                "Synthetic data: seeded generator, " + r.seed() + ". Re-running with the same seed reproduces the same inputs (but not identical timings).");
        var rec = runs.save("BENCHMARK", "Benchmark " + r.sizes(), "sizes " + r.sizes() + ", " + r.repetitions() + " reps, seed " + r.seed(), new BenchmarkView(null, null, report, notes));
        activity.log("BENCHMARK_RUN", "sizes " + r.sizes());
        return new BenchmarkView(rec.id, rec.createdAt, report, notes);
    }
    public List<RunSummary> history() { return runs.list("BENCHMARK"); }
    public JsonNode get(Long id) { return runs.payload(id, "BENCHMARK"); }
}
