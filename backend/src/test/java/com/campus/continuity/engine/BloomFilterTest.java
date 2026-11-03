package com.campus.continuity.engine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;

/** Correctness tests for the Bloom filter (BF-1 … BF-8). */
class BloomFilterTest {

    @Test @DisplayName("BF-1 NO FALSE NEGATIVES: every inserted key (20,000) is reported as possibly present")
    void noFalseNegatives() {
        BloomFilter f = new BloomFilter(20_000, 0.01);
        for (int i = 0; i < 20_000; i++) f.add("REQ-" + i);
        for (int i = 0; i < 20_000; i++) assertTrue(f.mightContain("REQ-" + i));
    }

    @Test @DisplayName("BF-2 sizing follows m = -n ln p /(ln2)^2 and k = (m/n) ln 2")
    void sizing() {
        BloomFilter f = new BloomFilter(1000, 0.01);
        assertEquals(9586, f.bitCount()); assertEquals(7, f.hashCount());
    }

    @Test @DisplayName("BF-3 observed false-positive rate stays near the configured 1% (< 2.5%)")
    void falsePositiveRate() {
        BloomFilter f = new BloomFilter(10_000, 0.01);
        for (int i = 0; i < 10_000; i++) f.add("in-" + i);
        int fp = 0, probes = 50_000;
        for (int i = 0; i < probes; i++) if (f.mightContain("out-" + i)) fp++;
        assertTrue((double) fp / probes < 0.025, "observed " + (double) fp / probes);
        assertTrue(Math.abs(f.theoreticalFpp() - 0.01) < 0.003);
    }

    @Test @DisplayName("BF-4 BOUNDARY: empty filter answers 'definitely absent' and has zero occupancy")
    void emptyFilter() {
        BloomFilter f = new BloomFilter(100, 0.05);
        assertFalse(f.mightContain("anything")); assertEquals(0, f.setBits()); assertEquals(0.0, f.estimatedFpp());
    }

    @Test @DisplayName("BF-5 INVALID: bad expected insertions or probability rejected; null item rejected")
    void invalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, 0.1).add(null));
    }

    @Test @DisplayName("BF-6 hash positions are deterministic, in range, and k of them are produced")
    void positions() {
        BloomFilter f = new BloomFilter(500, 0.01);
        int[] a = f.positions("REQ-ünï-✓"), b = f.positions("REQ-ünï-✓");
        assertArrayEquals(a, b); assertEquals(f.hashCount(), a.length);
        for (int p : a) assertTrue(p >= 0 && p < f.bitCount());
    }

    @Test @DisplayName("BF-7 occupancy grows with insertions and matches the bit string")
    void occupancy() {
        BloomFilter f = new BloomFilter(200, 0.01);
        double before = f.occupancy(); for (int i = 0; i < 100; i++) f.add("k" + i);
        assertTrue(f.occupancy() > before);
        assertEquals(f.setBits(), f.bitString().chars().filter(c -> c == '1').count());
        assertEquals(100, f.insertions());
    }

    @Test @DisplayName("BF-8 OVERFILL: inserting far beyond capacity raises the false-positive probability (graceful degradation, still no false negatives)")
    void overfill() {
        BloomFilter f = new BloomFilter(100, 0.01);
        for (int i = 0; i < 2000; i++) f.add("x" + i);
        assertTrue(f.estimatedFpp() > 0.5);
        for (int i = 0; i < 2000; i++) assertTrue(f.mightContain("x" + i));
    }
}
