package com.campus.continuity.bench;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.List;

/** Benchmark harness tests (BM-1 … BM-4). */
class BenchmarkRunnerTest {
    @Test @DisplayName("BM-1 three increasing sizes run; EK ≥ greedy everywhere; Bloom has zero false negatives")
    void threeSizes() {
        var r = BenchmarkRunner.run(List.of(100, 400, 1200), 3, 11L, 0.01);
        assertEquals(3, r.allocation().size());
        for (var a : r.allocation()) assertTrue(a.ekAllocated() >= a.greedyAllocated());
        for (var b : r.bloom()) { assertEquals(0, b.falseNegatives()); assertTrue(b.observedFpp() < 0.05); }
    }
    @Test @DisplayName("BM-2 datasets are reproducible for the same seed")
    void reproducible() { assertEquals(DatasetGenerator.generate(50, 5).teams(), DatasetGenerator.generate(50, 5).teams()); }
    @Test @DisplayName("BM-3 INVALID: empty size list, oversized size, bad repetitions rejected")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run(List.of(), 3, 1, 0.01));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run(List.of(99999), 3, 1, 0.01));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run(List.of(10), 0, 1, 0.01));
    }
    @Test @DisplayName("BM-4 BOUNDARY: single-team dataset works")
    void single() { assertEquals(1, BenchmarkRunner.run(List.of(1), 1, 1, 0.01).allocation().size()); }
}
