package com.campus.continuity.engine;

import java.util.*;

/**
 * Immutable engine-side domain model. The engine never touches JPA entities or Spring; the service layer
 * converts database rows into a {@link Snapshot}. All constructors validate their input so malformed
 * graphs (negative capacities, blank codes, duplicate codes) can never reach the flow network.
 */
public final class Model {
    private Model() {}

    public enum Availability { AVAILABLE, DISRUPTED, DEACTIVATED }

    static String reqCode(String c, String what) {
        if (c == null || c.isBlank()) throw new IllegalArgumentException(what + " code is required");
        return c.trim();
    }

    public static Set<String> normTags(Collection<String> in) {
        if (in == null) return Set.of();
        TreeSet<String> s = new TreeSet<>();
        for (String x : in) if (x != null && !x.isBlank()) s.add(x.trim().toLowerCase(Locale.ROOT));
        return Collections.unmodifiableSet(s);
    }

    public record Lab(String code, String name, int capacity, int maxTeamSize, Set<String> equipment,
                      Availability status, String reason) {
        public Lab {
            code = reqCode(code, "Laboratory");
            if (capacity < 0) throw new IllegalArgumentException("Laboratory " + code + ": capacity must not be negative");
            if (maxTeamSize < 1) throw new IllegalArgumentException("Laboratory " + code + ": max team size must be at least 1");
            equipment = normTags(equipment);
            status = status == null ? Availability.AVAILABLE : status;
            name = (name == null || name.isBlank()) ? code : name.trim();
        }
        public Lab withCapacity(int c) { return new Lab(code, name, c, maxTeamSize, equipment, status, reason); }
        public Lab withStatus(Availability s, String r) { return new Lab(code, name, capacity, maxTeamSize, equipment, s, r); }
        public Lab withEquipment(Set<String> e) { return new Lab(code, name, capacity, maxTeamSize, e, status, reason); }
    }

    public record Slot(String code, String label, int durationMinutes, Availability status, String reason) {
        public Slot {
            code = reqCode(code, "Time slot");
            if (durationMinutes < 1) throw new IllegalArgumentException("Time slot " + code + ": duration must be positive");
            status = status == null ? Availability.AVAILABLE : status;
            label = (label == null || label.isBlank()) ? code : label.trim();
        }
        public Slot withStatus(Availability s, String r) { return new Slot(code, label, durationMinutes, s, r); }
    }

    public record Team(String code, String name, int size, Set<String> requirements, int durationMinutes,
                       Set<String> eligibleSlots) {
        public Team {
            code = reqCode(code, "Team");
            if (size < 1) throw new IllegalArgumentException("Team " + code + ": size must be at least 1");
            if (durationMinutes < 1) throw new IllegalArgumentException("Team " + code + ": session duration must be positive");
            requirements = normTags(requirements);
            eligibleSlots = normTags(eligibleSlots);
            name = (name == null || name.isBlank()) ? code : name.trim();
        }
    }

    public record Snapshot(List<Lab> labs, List<Slot> slots, List<Team> teams) {
        public Snapshot {
            labs = List.copyOf(labs == null ? List.of() : labs);
            slots = List.copyOf(slots == null ? List.of() : slots);
            teams = List.copyOf(teams == null ? List.of() : teams);
            unique(labs.stream().map(Lab::code).toList(), "laboratory");
            unique(slots.stream().map(Slot::code).toList(), "time slot");
            unique(teams.stream().map(Team::code).toList(), "team");
        }
        private static void unique(List<String> codes, String what) {
            Set<String> seen = new HashSet<>();
            for (String c : codes) if (!seen.add(c.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate " + what + " code: " + c);
        }
    }
}
