package com.campus.continuity.service;

import com.campus.continuity.domain.*;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.Model;
import com.campus.continuity.repo.Repos.*;
import com.campus.continuity.sim.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** What-if simulation. Every run re-executes Edmonds–Karp for the baseline AND the scenario on a fresh database snapshot. */
@Service
public class SimulationService {
    public record SimulationRequest(String name, List<Mutation> mutations) {}
    public record SimulationView(Long id, Instant createdAt, String name, List<Mutation> mutations, ScenarioSimulator.ScenarioResult result) {}
    public record ApplyResult(List<String> changes) {}

    private final CatalogService catalog; private final RunStore runs; private final ActivityService activity;
    private final LabRepo labs; private final SlotRepo slots; private final TeamRepo teams; private final TeamService teamService;

    public SimulationService(CatalogService catalog, RunStore runs, ActivityService activity, LabRepo labs, SlotRepo slots, TeamRepo teams, TeamService teamService) {
        this.catalog = catalog; this.runs = runs; this.activity = activity; this.labs = labs; this.slots = slots; this.teams = teams; this.teamService = teamService;
    }

    public SimulationView run(SimulationRequest req) {
        String name = req.name() == null || req.name().isBlank() ? "Scenario " + Instant.now() : req.name().trim();
        var result = ScenarioSimulator.run(catalog.snapshot(), req.mutations());
        var rec = runs.save("SIMULATION", name, result.before().allocated() + " → " + result.after().allocated() + " allocated ("
                + (result.diff().allocatedDelta() >= 0 ? "+" : "") + result.diff().allocatedDelta() + "); displaced " + result.diff().newlyDisplaced().size()
                + ", recovered " + result.diff().newlyRecovered().size(), new SimulationView(null, null, name, req.mutations(), result));
        activity.log("SIMULATION_RUN", name + ": " + result.before().allocated() + " → " + result.after().allocated());
        return new SimulationView(rec.id, rec.createdAt, name, req.mutations(), result);
    }

    public List<RunSummary> history() { return runs.list("SIMULATION"); }
    public JsonNode get(Long id) { return runs.payload(id, "SIMULATION"); }

    /** Commit a scenario to the real database (labs, slots, new teams) after the administrator confirms it. */
    @Transactional
    public ApplyResult apply(List<Mutation> mutations) {
        var applied = ScenarioSimulator.apply(catalog.snapshot(), mutations);
        for (Model.Lab l : applied.snapshot().labs()) {
            Laboratory e = labs.findByCodeIgnoreCase(l.code()).orElseThrow();
            e.capacity = l.capacity(); e.status = l.status(); e.reason = l.reason(); e.equipment = Mappers.join(l.equipment());
        }
        for (Model.Slot s : applied.snapshot().slots()) {
            TimeSlot e = slots.findByCodeIgnoreCase(s.code()).orElseThrow();
            e.status = s.status(); e.reason = s.reason();
        }
        for (Model.Team t : applied.snapshot().teams())
            if (!teams.existsByCodeIgnoreCase(t.code()))
                teamService.register(new TeamRequest("SIM-" + t.code(), t.code(), t.name(), t.size(), new ArrayList<>(t.requirements()), t.durationMinutes(),
                        new ArrayList<>(t.eligibleSlots())), "SIMULATION", false);
        activity.log("SCENARIO_APPLIED", String.join("; ", applied.changes()));
        return new ApplyResult(applied.changes());
    }
}
