package com.campus.continuity.bench;

import com.campus.continuity.engine.*;
import com.campus.continuity.engine.Model.Snapshot;
import java.util.*;

/** Empirical benchmarks. Reports medians over repetitions after a warm-up; theory is reported separately by the UI/docs. */
public final class BenchmarkRunner {
    private BenchmarkRunner() {}

    public record AllocRow(int teams, int labs, int slots, int graphNodes, int graphEdges, long ekAllocated, long greedyAllocated,
                           long greedyWorstOrderAllocated, double ekTotalMs, double ekFlowMs, double greedyMs,
                           long bfsRuns, long edgesScanned, long augmentations, long greedyChecks) {}
    public record BloomRow(int keys, int bits, int hashes, double bloomLookupMs, double exactLookupMs, double hybridLookupMs,
                           int probes, int absentProbes, int falsePositives, double observedFpp, double targetFpp,
                           double theoreticalFpp, int falseNegatives) {}
    public record Report(long seed, int repetitions, List<AllocRow> allocation, List<BloomRow> bloom) {}

    public static Report run(List<Integer> sizes, int reps, long seed, double fpp) {
        if (sizes == null || sizes.size() < 1 || sizes.size() > 8) throw new IllegalArgumentException("Provide between 1 and 8 dataset sizes");
        if (reps < 1 || reps > 15) throw new IllegalArgumentException("repetitions must be between 1 and 15");
        for (int s : sizes) if (s < 1 || s > 3_000) throw new IllegalArgumentException("Each size must be between 1 and 3000");
        List<AllocRow> a = new ArrayList<>();
        List<BloomRow> b = new ArrayList<>();
        for (int n : sizes) { a.add(allocation(n, reps, seed)); b.add(bloom(n, reps, seed, fpp)); }
        return new Report(seed, reps, a, b);
    }

    static AllocRow allocation(int n, int reps, long seed) {
        Snapshot snap = DatasetGenerator.generate(n, seed);
        AllocationOutcome warm = AllocationEngine.solve(snap, false);
        GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT);
        double[] tot = new double[reps], flow = new double[reps], gr = new double[reps];
        AllocationOutcome last = warm; GreedyAllocator.Result g = null;
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            last = AllocationEngine.solve(snap, false);
            tot[i] = (System.nanoTime() - t0) / 1e6;
            flow[i] = last.stats().flowNanos() / 1e6;
            g = GreedyAllocator.allocate(snap, GreedyAllocator.Order.INPUT);
            gr[i] = g.nanos() / 1e6;
        }
        int worst = Integer.MAX_VALUE;
        for (GreedyAllocator.Order o : GreedyAllocator.Order.values()) worst = Math.min(worst, GreedyAllocator.allocate(snap, o).allocated());
        var st = last.stats();
        return new AllocRow(n, snap.labs().size(), snap.slots().size(), st.nodes(), st.edges(), last.allocated(), g.allocated(), worst,
                median(tot), median(flow), median(gr), st.bfsRuns(), st.edgesScanned(), st.augmentations(), g.compatChecks());
    }

    static BloomRow bloom(int n, int reps, long seed, double fpp) {
        List<String> present = new ArrayList<>(), absent = new ArrayList<>();
        for (int i = 0; i < n; i++) { present.add("REQ-" + seed + "-" + i); absent.add("REQ-" + seed + "-X" + i); }
        BloomFilter bf = new BloomFilter(n, fpp);
        HashSet<String> exact = new HashSet<>(Math.max(16, n * 2));
        for (String s : present) { bf.add(s); exact.add(s); }
        int fn = 0;
        for (String s : present) if (!bf.mightContain(s)) fn++;
        int fp = 0;
        for (String s : absent) if (bf.mightContain(s)) fp++;
        List<String> probes = new ArrayList<>(present); probes.addAll(absent);
        Collections.shuffle(probes, new Random(seed));
        double[] tb = new double[reps], te = new double[reps], th = new double[reps];
        long sink = 0;
        for (int w = 0; w < 2; w++) for (String p : probes) if (bf.mightContain(p)) sink++; // warm-up
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            for (String p : probes) if (bf.mightContain(p)) sink++;
            tb[i] = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            for (String p : probes) if (exact.contains(p)) sink++;
            te[i] = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            for (String p : probes) if (bf.mightContain(p) && exact.contains(p)) sink++;
            th[i] = (System.nanoTime() - t0) / 1e6;
        }
        if (sink == -1) System.out.print(""); // keep JIT honest
        return new BloomRow(n, bf.bitCount(), bf.hashCount(), median(tb), median(te), median(th), probes.size(), absent.size(), fp,
                (double) fp / absent.size(), fpp, bf.theoreticalFpp(), fn);
    }

    static double median(double[] v) { double[] c = v.clone(); Arrays.sort(c); return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2; }
}
