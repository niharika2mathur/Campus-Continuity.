package com.campus.continuity.service;

import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.AllocationEngine;
import com.campus.continuity.engine.AllocationOutcome;
import com.campus.continuity.engine.Model;
import com.campus.continuity.engine.Model.Availability;
import com.campus.continuity.repo.Repos.ActivityRepo;
import org.springframework.stereotype.Service;
import java.util.*;

/** Dashboard figures are computed from a LIVE Edmonds–Karp run on the current database state — never from stale stored results. */
@Service
public class DashboardService {
    public record Disruption(String kind, String code, String status, String reason) {}
    public record DashboardView(int labsTotal, int labsAvailable, int labsDisrupted, int labsDeactivated, int slotsTotal, int slotsDisrupted,
                                int teams, int allocated, int unallocated, double utilization, int totalSeats, long planMillis,
                                List<Disruption> disruptions, List<AllocationOutcome.LabLoad> labLoads, List<String> bottleneck,
                                List<RunSummary> recentRuns, List<ActivityView> recentActivity, BloomScreeningService.Stats bloom) {}

    private final CatalogService catalog; private final RunStore runs; private final ActivityRepo activity; private final BloomScreeningService bloom;
    public DashboardService(CatalogService catalog, RunStore runs, ActivityRepo activity, BloomScreeningService bloom) {
        this.catalog = catalog; this.runs = runs; this.activity = activity; this.bloom = bloom;
    }

    public DashboardView get() {
        Model.Snapshot s = catalog.snapshot();
        long t0 = System.nanoTime();
        AllocationOutcome o = AllocationEngine.solve(s, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        List<Disruption> d = new ArrayList<>();
        s.labs().stream().filter(l -> l.status() != Availability.AVAILABLE).forEach(l -> d.add(new Disruption("LAB", l.code(), l.status().name(), l.reason())));
        s.slots().stream().filter(x -> x.status() != Availability.AVAILABLE).forEach(x -> d.add(new Disruption("SLOT", x.code(), x.status().name(), x.reason())));
        int av = (int) s.labs().stream().filter(l -> l.status() == Availability.AVAILABLE).count();
        int dis = (int) s.labs().stream().filter(l -> l.status() == Availability.DISRUPTED).count();
        int dea = (int) s.labs().stream().filter(l -> l.status() == Availability.DEACTIVATED).count();
        int sd = (int) s.slots().stream().filter(x -> x.status() != Availability.AVAILABLE).count();
        return new DashboardView(s.labs().size(), av, dis, dea, s.slots().size(), sd, s.teams().size(), o.allocated(), o.unallocatedCount(), o.utilization(),
                o.totalSeats(), ms, d, o.labLoads(), o.bottleneck().lines(), runs.recent(),
                activity.findTop200ByOrderByIdDesc().stream().limit(10).map(Mappers::view).toList(), bloom.stats());
    }
}
