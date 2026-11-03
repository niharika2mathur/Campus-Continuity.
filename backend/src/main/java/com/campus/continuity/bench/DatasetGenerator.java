package com.campus.continuity.bench;

import com.campus.continuity.engine.Model.*;
import java.util.*;

/** Reproducible synthetic campus generator (same seed + size ⇒ identical dataset). */
public final class DatasetGenerator {
    private DatasetGenerator() {}
    static final String[] TAGS = {"gpu", "linux", "matlab", "fpga", "3d-printer", "vr-kit", "oscilloscope", "cad"};

    public static Snapshot generate(int teams, long seed) {
        if (teams < 1 || teams > 200_000) throw new IllegalArgumentException("teams must be between 1 and 200000");
        Random r = new Random(seed);
        int nSlots = 10;
        int nLabs = Math.max(3, (int) Math.ceil(teams / 30.0));
        List<Slot> slots = new ArrayList<>();
        for (int s = 0; s < nSlots; s++) slots.add(new Slot("S" + s, "Slot " + s, 90 + 30 * (s % 3), Availability.AVAILABLE, null));
        List<Lab> labs = new ArrayList<>();
        for (int l = 0; l < nLabs; l++) {
            Set<String> eq = new TreeSet<>();
            int k = 2 + r.nextInt(4);
            while (eq.size() < k) eq.add(TAGS[r.nextInt(TAGS.length)]);
            labs.add(new Lab("L" + l, "Lab " + l, 2 + r.nextInt(3), 3 + r.nextInt(3), eq, Availability.AVAILABLE, null));
        }
        List<Team> list = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            Set<String> req = new TreeSet<>();
            int k = r.nextInt(3);
            for (int q = 0; q < k; q++) req.add(TAGS[r.nextInt(TAGS.length)]);
            Set<String> elig = new TreeSet<>();
            if (r.nextBoolean()) { int e = 2 + r.nextInt(4); while (elig.size() < e) elig.add("s" + r.nextInt(nSlots)); }
            list.add(new Team("T" + i, "Team " + i, 2 + r.nextInt(4), req, 60 + 30 * r.nextInt(3), elig));
        }
        return new Snapshot(labs, slots, list);
    }
}
