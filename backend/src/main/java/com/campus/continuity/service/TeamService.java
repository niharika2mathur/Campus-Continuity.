package com.campus.continuity.service;

import com.campus.continuity.domain.*;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.Compat;
import com.campus.continuity.engine.Model;
import com.campus.continuity.engine.Model.Availability;
import com.campus.continuity.repo.Repos.*;
import com.campus.continuity.web.ApiExceptions.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Team registration. EVERY new request identifier passes through the Bloom filter and, on a possible positive, an exact database check. */
@Service
public class TeamService {
    private final TeamRepo teams; private final LabRepo labs; private final SlotRepo slots; private final RequestLogRepo logs;
    private final BloomScreeningService bloom; private final ActivityService activity;

    public TeamService(TeamRepo teams, LabRepo labs, SlotRepo slots, RequestLogRepo logs, BloomScreeningService bloom, ActivityService activity) {
        this.teams = teams; this.labs = labs; this.slots = slots; this.logs = logs; this.bloom = bloom; this.activity = activity;
    }

    @Transactional(readOnly = true) public List<TeamView> list() { return teams.findAll(Sort.by("id")).stream().map(Mappers::view).toList(); }
    @Transactional(readOnly = true) public TeamView get(Long id) { return Mappers.view(find(id)); }
    @Transactional(readOnly = true) public List<RequestLogView> requestLog() { return logs.findTop300ByOrderByIdDesc().stream().map(Mappers::view).toList(); }

    public static String normalizeRequestId(String id) { return id.trim().toUpperCase(Locale.ROOT); }

    @Transactional(noRollbackFor = {ConflictException.class, IllegalArgumentException.class})
    public synchronized RegisterResult register(TeamRequest r, String source, boolean strict) {
        String rid = normalizeRequestId(r.requestId());
        BloomScreeningService.Screening s = bloom.screen(rid, teams::existsByRequestId);
        String code = r.code().trim();
        if (s.duplicate()) {
            log(rid, code, s, "REJECTED_DUPLICATE", source, "Request identifier already submitted");
            throw new ConflictException("Duplicate request: " + rid + " was already submitted (confirmed by exact lookup after Bloom filter positive)");
        }
        if (teams.existsByCodeIgnoreCase(code)) {
            log(rid, code, s, "REJECTED_INVALID", source, "Team code already exists");
            throw new ConflictException("Team code already exists: " + code);
        }
        Team t = new Team();
        t.requestId = rid; t.code = code; t.name = r.name() == null || r.name().isBlank() ? code : r.name().trim();
        t.size = r.size(); t.durationMinutes = r.durationMinutes();
        t.requirements = Mappers.join(r.requirements()); t.eligibleSlots = Mappers.join(r.eligibleSlots());
        List<String> warnings = compatibilityWarnings(Mappers.toModel(t));
        if (strict && !warnings.isEmpty() && warnings.get(0).startsWith("INCOMPATIBLE")) {
            log(rid, code, s, "REJECTED_INVALID", source, warnings.get(0));
            throw new IllegalArgumentException(warnings.get(0));
        }
        teams.save(t);
        bloom.recordAccepted(rid);
        log(rid, code, s, "ACCEPTED", source, warnings.isEmpty() ? null : warnings.get(0));
        activity.log("TEAM_REGISTERED", code + " (" + source + ")");
        return new RegisterResult(Mappers.view(t), new ScreeningView(s.bloomResult(), s.exactResult(), "ACCEPTED"), warnings);
    }

    @Transactional
    public TeamView update(Long id, TeamRequest r) {
        Team t = find(id);
        teams.findByCodeIgnoreCase(r.code().trim()).filter(x -> !x.id.equals(id)).ifPresent(x -> { throw new ConflictException("Team code already exists: " + r.code()); });
        t.code = r.code().trim(); t.name = r.name() == null || r.name().isBlank() ? t.code : r.name().trim();
        t.size = r.size(); t.durationMinutes = r.durationMinutes();
        t.requirements = Mappers.join(r.requirements()); t.eligibleSlots = Mappers.join(r.eligibleSlots());
        activity.log("TEAM_UPDATED", t.code);
        return Mappers.view(t);
    }

    @Transactional
    public void delete(Long id) {
        Team t = find(id);
        teams.delete(t);
        activity.log("TEAM_DELETED", t.code + " (request id stays in the Bloom filter; exact lookup will correct it as a false positive)");
    }

    /** Is the request satisfiable by ANY laboratory/slot if everything were available? Warns when only disrupted resources fit. */
    List<String> compatibilityWarnings(Model.Team t) {
        List<Model.Lab> ls = labs.findAll().stream().map(Mappers::toModel).toList();
        List<Model.Slot> ss = slots.findAll().stream().map(Mappers::toModel).toList();
        if (ls.isEmpty() || ss.isEmpty()) return List.of("No laboratories or slots defined yet; compatibility cannot be assessed.");
        boolean okNow = false, okIfRestored = false;
        EnumMap<Compat.Reason, Integer> why = new EnumMap<>(Compat.Reason.class);
        for (Model.Lab l : ls) for (Model.Slot s : ss) {
            Compat.Reason now = Compat.check(t, l, s);
            if (now == Compat.Reason.OK) okNow = true;
            Compat.Reason ideal = Compat.check(t, l.withStatus(Availability.AVAILABLE, null).withCapacity(Math.max(1, l.capacity())), s.withStatus(Availability.AVAILABLE, null));
            if (ideal == Compat.Reason.OK) okIfRestored = true; else why.merge(ideal, 1, Integer::sum);
        }
        if (okNow) return List.of();
        if (okIfRestored) return List.of("Only compatible with resources that are currently unavailable or have zero capacity.");
        Compat.Reason top = why.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Compat.Reason.MISSING_EQUIPMENT);
        return List.of("INCOMPATIBLE: no laboratory/slot can host this team (most common blocker: " + top.text + ").");
    }

    private void log(String rid, String code, BloomScreeningService.Screening s, String outcome, String source, String msg) {
        RequestLog l = new RequestLog();
        l.requestId = rid; l.teamCode = code; l.bloomResult = s.bloomResult(); l.exactResult = s.exactResult();
        l.outcome = outcome; l.source = source; l.message = msg;
        logs.save(l);
    }

    private Team find(Long id) { return teams.findById(id).orElseThrow(() -> new NotFoundException("Team " + id + " not found")); }
}
