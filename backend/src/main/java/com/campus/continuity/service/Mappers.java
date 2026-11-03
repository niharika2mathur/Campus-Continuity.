package com.campus.continuity.service;

import com.campus.continuity.domain.*;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.Model;
import java.util.*;

/** Entity ⇄ engine model ⇄ DTO conversions. */
public final class Mappers {
    private Mappers() {}

    public static String join(Collection<String> tags) { return String.join(",", Model.normTags(tags)); }
    public static List<String> split(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public static Model.Lab toModel(Laboratory l) {
        return new Model.Lab(l.code, l.name, l.capacity, l.maxTeamSize, new TreeSet<>(split(l.equipment)), l.status, l.reason);
    }
    public static Model.Slot toModel(TimeSlot s) { return new Model.Slot(s.code, s.label, s.durationMinutes, s.status, s.reason); }
    public static Model.Team toModel(Team t) {
        return new Model.Team(t.code, t.name, t.size, new TreeSet<>(split(t.requirements)), t.durationMinutes, new TreeSet<>(split(t.eligibleSlots)));
    }
    public static LabView view(Laboratory l) {
        return new LabView(l.id, l.code, l.name, l.building, l.capacity, l.maxTeamSize, split(l.equipment), l.status, l.reason);
    }
    public static SlotView view(TimeSlot s) {
        return new SlotView(s.id, s.code, s.label, s.dayOfWeek, s.startTime, s.durationMinutes, s.status, s.reason);
    }
    public static TeamView view(Team t) {
        return new TeamView(t.id, t.requestId, t.code, t.name, t.size, split(t.requirements), t.durationMinutes, split(t.eligibleSlots), t.createdAt);
    }
    public static RequestLogView view(RequestLog r) {
        return new RequestLogView(r.id, r.requestId, r.teamCode, r.bloomResult, r.exactResult, r.outcome, r.source, r.message, r.createdAt);
    }
    public static RunSummary view(RunRecord r) { return new RunSummary(r.id, r.kind, r.name, r.summary, r.createdAt); }
    public static ActivityView view(ActivityLog a) { return new ActivityView(a.id, a.action, a.detail, a.createdAt); }
}
