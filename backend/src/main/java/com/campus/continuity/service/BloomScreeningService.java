package com.campus.continuity.service;

import com.campus.continuity.engine.BloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Owns two Bloom filters built on the self-implemented {@link BloomFilter}:
 *  - LIVE: screens every real request identifier before it is accepted. Positives are ALWAYS verified against the
 *    authoritative store (the team table) via the injected exact-lookup predicate.
 *  - SANDBOX: an isolated, resizable filter (with its own exact set) for the interactive Bloom page.
 */
@Service
public class BloomScreeningService {
    public record Stats(long insertions, long queries, long negativePrechecks, long possiblePositives, long confirmedDuplicates,
                        long falsePositives, double falsePositiveRate) {}
    public record Screening(String bloomResult, String exactResult, boolean duplicate) {}
    public record State(int bits, int hashes, int expectedInsertions, double targetFpp, long insertions, int setBits, double occupancy,
                        double estimatedFpp, double theoreticalFpp, String bitString, Stats stats) {}
    public record QueryResult(String id, int[] positions, boolean[] bitsAtPositions, String bloomResult, String exactResult,
                              String classification, String explanation) {}

    private final int configuredN; private final double configuredP;
    private volatile BloomFilter live;
    private final AtomicLong insertions = new AtomicLong(), queries = new AtomicLong(), negatives = new AtomicLong(),
            positives = new AtomicLong(), confirmed = new AtomicLong(), falsePos = new AtomicLong();

    // sandbox
    private BloomFilter box; private final Set<String> boxExact = new HashSet<>();
    private final AtomicLong boxQ = new AtomicLong(), boxNeg = new AtomicLong(), boxPos = new AtomicLong(), boxTrue = new AtomicLong(), boxFalse = new AtomicLong();

    public BloomScreeningService(@Value("${app.bloom.expected-insertions}") int n, @Value("${app.bloom.false-positive-probability}") double p) {
        this.configuredN = n; this.configuredP = p;
        this.live = new BloomFilter(n, p);
        this.box = new BloomFilter(64, 0.05);
    }

    /** Rebuild from the authoritative store (application start, reset, import). Counters restart. */
    public synchronized void rebuild(Collection<String> existingIds) {
        int n = Math.max(configuredN, existingIds.size() * 2);
        BloomFilter f = new BloomFilter(n, configuredP);
        existingIds.forEach(f::add);
        live = f;
        for (AtomicLong a : List.of(insertions, queries, negatives, positives, confirmed, falsePos)) a.set(0);
        insertions.set(existingIds.size());
    }

    /** Screen one identifier. Does not insert; call {@link #recordAccepted} after the record is persisted. */
    public synchronized Screening screen(String id, Predicate<String> existsExact) {
        queries.incrementAndGet();
        if (!live.mightContain(id)) { negatives.incrementAndGet(); return new Screening("NEGATIVE", "NOT_CHECKED", false); }
        positives.incrementAndGet();
        if (existsExact.test(id)) { confirmed.incrementAndGet(); return new Screening("POSSIBLE", "PRESENT", true); }
        falsePos.incrementAndGet();
        return new Screening("POSSIBLE", "ABSENT", false);
    }

    public synchronized void recordAccepted(String id) { live.add(id); insertions.incrementAndGet(); }

    /** Read-only probe used by the UI (counts as a query, never inserts). */
    public synchronized QueryResult probe(String id, Predicate<String> existsExact) {
        Screening s = screen(id, existsExact);
        int[] pos = live.positions(id);
        boolean[] bits = new boolean[pos.length];
        for (int i = 0; i < pos.length; i++) bits[i] = live.bit(pos[i]);
        return new QueryResult(id, pos, bits, s.bloomResult(), s.exactResult(), classify(s), explain(s));
    }

    public State state() {
        BloomFilter f = live;
        return new State(f.bitCount(), f.hashCount(), f.expectedInsertions(), f.targetFpp(), f.insertions(), f.setBits(), f.occupancy(),
                f.estimatedFpp(), f.theoreticalFpp(), f.bitString(), stats());
    }

    public Stats stats() {
        long neg = negatives.get(), fp = falsePos.get();
        long absentQueries = neg + fp; // queries whose true answer is "absent"
        return new Stats(insertions.get(), queries.get(), neg, positives.get(), confirmed.get(), fp, absentQueries == 0 ? 0 : (double) fp / absentQueries);
    }

    // ---------- sandbox ----------
    public synchronized State sandboxReset(int n, double p) {
        box = new BloomFilter(n, p); boxExact.clear();
        for (AtomicLong a : List.of(boxQ, boxNeg, boxPos, boxTrue, boxFalse)) a.set(0);
        return sandboxState();
    }
    public synchronized QueryResult sandboxAdd(String id) {
        int[] pos = box.positions(id);
        box.add(id); boxExact.add(id);
        boolean[] bits = new boolean[pos.length]; Arrays.fill(bits, true);
        return new QueryResult(id, pos, bits, "INSERTED", "PRESENT", "INSERTED", "Set the " + pos.length + " bits shown. Bits are never cleared, so this item can no longer produce a false negative.");
    }
    public synchronized QueryResult sandboxQuery(String id) {
        boxQ.incrementAndGet();
        int[] pos = box.positions(id);
        boolean[] bits = new boolean[pos.length];
        for (int i = 0; i < pos.length; i++) bits[i] = box.bit(pos[i]);
        boolean maybe = box.mightContain(id), exact = boxExact.contains(id);
        if (!maybe) { boxNeg.incrementAndGet(); return new QueryResult(id, pos, bits, "NEGATIVE", "NOT_CHECKED", "DEFINITELY_ABSENT", "At least one hash position is 0, so the item was never inserted. This answer is certain."); }
        boxPos.incrementAndGet();
        if (exact) { boxTrue.incrementAndGet(); return new QueryResult(id, pos, bits, "POSSIBLE", "PRESENT", "TRUE_POSITIVE", "All positions are 1 and the exact set confirms the item is present."); }
        boxFalse.incrementAndGet();
        return new QueryResult(id, pos, bits, "POSSIBLE", "ABSENT", "FALSE_POSITIVE", "All positions are 1 only because other items set those bits. The exact set shows the item is absent — this is a false positive.");
    }
    public synchronized State sandboxState() {
        long fp = boxFalse.get(), neg = boxNeg.get();
        Stats st = new Stats(box.insertions(), boxQ.get(), neg, boxPos.get(), boxTrue.get(), fp, (neg + fp) == 0 ? 0 : (double) fp / (neg + fp));
        return new State(box.bitCount(), box.hashCount(), box.expectedInsertions(), box.targetFpp(), box.insertions(), box.setBits(), box.occupancy(),
                box.estimatedFpp(), box.theoreticalFpp(), box.bitString(), st);
    }

    private static String classify(Screening s) {
        return s.bloomResult().equals("NEGATIVE") ? "DEFINITELY_ABSENT" : s.duplicate() ? "CONFIRMED_DUPLICATE" : "FALSE_POSITIVE";
    }
    private static String explain(Screening s) {
        return switch (classify(s)) {
            case "DEFINITELY_ABSENT" -> "At least one hash position is 0: the identifier is certainly new. No database lookup was needed.";
            case "CONFIRMED_DUPLICATE" -> "All positions were 1 (possibly present). The authoritative database lookup confirmed the identifier already exists.";
            default -> "All positions were 1 (possibly present) but the authoritative database lookup found nothing: a false positive.";
        };
    }
}
