package com.campus.continuity.engine;

import java.nio.charset.StandardCharsets;

/**
 * Self-implemented Bloom filter over a long[] bit array.
 * Sizing (standard): m = ceil(-n ln p / (ln 2)^2) bits, k = round((m/n) ln 2) hash functions.
 * Hashing (Kirsch–Mitzenmacher double hashing): position_i = (h1 + i·h2) mod m, i = 0..k-1, where
 *   h1 = 64-bit FNV-1a of the UTF-8 bytes,
 *   h2 = splitmix64 finaliser applied to a second FNV-1a pass with a different offset basis, forced odd.
 * Semantics: mightContain == false ⇒ DEFINITELY absent (no false negatives, because bits are never cleared).
 *            mightContain == true  ⇒ POSSIBLY present; callers needing exactness must verify against the authoritative store.
 */
public final class BloomFilter {
    private final long[] words;
    private final int m, k, expected;
    private final double targetFpp;
    private long inserted;

    public BloomFilter(int expectedInsertions, double falsePositiveProbability) {
        if (expectedInsertions < 1 || expectedInsertions > 10_000_000)
            throw new IllegalArgumentException("expectedInsertions must be between 1 and 10,000,000");
        if (!(falsePositiveProbability > 0.0 && falsePositiveProbability < 1.0))
            throw new IllegalArgumentException("falsePositiveProbability must be in (0,1)");
        this.expected = expectedInsertions;
        this.targetFpp = falsePositiveProbability;
        double ln2 = Math.log(2);
        this.m = (int) Math.max(64, Math.ceil(-expectedInsertions * Math.log(falsePositiveProbability) / (ln2 * ln2)));
        this.k = Math.max(1, (int) Math.round((double) m / expectedInsertions * ln2));
        this.words = new long[(m + 63) >>> 6];
    }

    private static long fnv(byte[] data, long basis) {
        long h = basis;
        for (byte b : data) { h ^= (b & 0xffL); h *= 0x100000001b3L; }
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public int[] positions(String item) {
        if (item == null) throw new IllegalArgumentException("item must not be null");
        byte[] d = item.getBytes(StandardCharsets.UTF_8);
        long h1 = fnv(d, 0xcbf29ce484222325L);
        long h2 = mix(fnv(d, 0x84222325cbf29ce4L)) | 1L;
        int[] p = new int[k];
        for (int i = 0; i < k; i++) p[i] = (int) Math.floorMod(h1 + (long) i * h2, (long) m);
        return p;
    }

    public synchronized void add(String item) {
        for (int p : positions(item)) words[p >>> 6] |= 1L << (p & 63);
        inserted++;
    }

    public synchronized boolean mightContain(String item) {
        for (int p : positions(item)) if ((words[p >>> 6] & (1L << (p & 63))) == 0) return false;
        return true;
    }

    public synchronized boolean bit(int i) { return (words[i >>> 6] & (1L << (i & 63))) != 0; }

    public synchronized int setBits() { int c = 0; for (long w : words) c += Long.bitCount(w); return c; }
    public int bitCount() { return m; }
    public int hashCount() { return k; }
    public int expectedInsertions() { return expected; }
    public double targetFpp() { return targetFpp; }
    public long insertions() { return inserted; }
    public double occupancy() { return (double) setBits() / m; }
    /** Estimate from the current fill ratio: (set bits / m)^k. */
    public double estimatedFpp() { return Math.pow(occupancy(), k); }
    /** Textbook prediction for the number of insertions so far: (1 - e^{-kn/m})^k. */
    public double theoreticalFpp() { return Math.pow(1 - Math.exp(-(double) k * inserted / m), k); }

    public synchronized String bitString() {
        StringBuilder sb = new StringBuilder(m);
        for (int i = 0; i < m; i++) sb.append(bit(i) ? '1' : '0');
        return sb.toString();
    }
}
