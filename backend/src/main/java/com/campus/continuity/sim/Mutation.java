package com.campus.continuity.sim;

import java.util.List;

/** One what-if change. Only the fields relevant to the type are used. */
public record Mutation(Type type, String target, Integer amount, String reason, List<String> equipment,
                       String teamCode, Integer teamSize, List<String> requirements, Integer durationMinutes,
                       List<String> eligibleSlots, Integer count) {
    public enum Type { DISABLE_LAB, RESTORE_LAB, REDUCE_CAPACITY, INCREASE_CAPACITY, DISABLE_SLOT, RESTORE_SLOT,
        ADD_EQUIPMENT, REMOVE_EQUIPMENT, ADD_TEAMS }
}
