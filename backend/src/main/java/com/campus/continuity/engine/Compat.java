package com.campus.continuity.engine;

import com.campus.continuity.engine.Model.*;
import java.util.*;

/** Compatibility rules between a team and a (laboratory, slot) pair. Single source of truth for engine, greedy and explanations. */
public final class Compat {
    private Compat() {}

    public enum Reason {
        OK("compatible"), LAB_UNAVAILABLE("laboratory unavailable"), SLOT_UNAVAILABLE("time slot unavailable"),
        ZERO_CAPACITY("laboratory capacity is zero"), MISSING_EQUIPMENT("required equipment/software missing"),
        TEAM_TOO_LARGE("team larger than laboratory limit"), SESSION_TOO_LONG("session longer than slot"),
        SLOT_NOT_ELIGIBLE("slot not in team's eligible slots");
        public final String text;
        Reason(String t) { text = t; }
    }

    /** One usable (lab, slot) pair; its capacity is the number of teams that may use it. */
    public record SlotNode(Lab lab, Slot slot, int capacity) {
        public String label() { return lab.code() + " / " + slot.code(); }
    }

    public static Reason check(Team t, Lab l, Slot s) {
        if (l.status() != Availability.AVAILABLE) return Reason.LAB_UNAVAILABLE;
        if (s.status() != Availability.AVAILABLE) return Reason.SLOT_UNAVAILABLE;
        if (l.capacity() <= 0) return Reason.ZERO_CAPACITY;
        if (!l.equipment().containsAll(t.requirements())) return Reason.MISSING_EQUIPMENT;
        if (t.size() > l.maxTeamSize()) return Reason.TEAM_TOO_LARGE;
        if (t.durationMinutes() > s.durationMinutes()) return Reason.SESSION_TOO_LONG;
        if (!t.eligibleSlots().isEmpty() && !t.eligibleSlots().contains(s.code().toLowerCase(Locale.ROOT))) return Reason.SLOT_NOT_ELIGIBLE;
        return Reason.OK;
    }

    /** Usable nodes: available lab, available slot, capacity &gt; 0, in deterministic lab-major order. */
    public static List<SlotNode> slotNodes(Snapshot s) {
        List<SlotNode> out = new ArrayList<>();
        for (Lab l : s.labs()) {
            if (l.status() != Availability.AVAILABLE || l.capacity() <= 0) continue;
            for (Slot sl : s.slots()) if (sl.status() == Availability.AVAILABLE) out.add(new SlotNode(l, sl, l.capacity()));
        }
        return out;
    }
}
