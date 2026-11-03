package com.campus.continuity.service;

import com.campus.continuity.domain.RunRecord;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.*;
import com.campus.continuity.engine.Model.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Runs Edmonds–Karp (Java engine) on a fresh snapshot of the database on every call, plus the greedy baseline for comparison. */
@Service
public class AllocationService {
    public record GreedySummary(String order, int allocated, int unallocated, long nanos, long compatChecks) {}
    public record AllocationRunView(Long id, Instant createdAt, String scenarioName, String source, AllocationOutcome outcome,
                                    List<GreedySummary> greedy, List<String> comparison) {}

    private final CatalogService catalog; private final RunStore runs; private final ActivityService activity; private final int maxTraceNodes;

    public AllocationService(CatalogService catalog, RunStore runs, ActivityService activity, @Value("${app.trace.max-nodes}") int maxTraceNodes) {
        this.catalog = catalog; this.runs = runs; this.activity = activity; this.maxTraceNodes = maxTraceNodes;
    }

    public AllocationRunView run(AllocationRequest req) {
        Snapshot snap = catalog.snapshot();
        String name = req.scenarioName() == null || req.scenarioName().isBlank() ? "Allocation " + Instant.now() : req.scenarioName().trim();
        AllocationRunView v = evaluate(snap, name, "LIVE", req.includeTrace());
        RunRecord rec = runs.save("ALLOCATION", name, v.outcome().allocated() + " of " + v.outcome().totalTeams() + " teams allocated; greedy(INPUT) "
                + v.greedy().get(0).allocated(), new AllocationRunView(null, null, name, "LIVE", v.outcome().withoutTrace(), v.greedy(), v.comparison()));
        activity.log("ALLOCATION_RUN", name + ": " + v.outcome().allocated() + "/" + v.outcome().totalTeams());
        return new AllocationRunView(rec.id, rec.createdAt, name, "LIVE", v.outcome(), v.greedy(), v.comparison());
    }

    /** Pure evaluation (no persistence); shared with the Observatory. */
    public AllocationRunView evaluate(Snapshot snap, String name, String source, boolean trace) {
        if (trace) {
            int nodes = snap.teams().size() + Compat.slotNodes(snap).size() + 2;
            if (nodes > maxTraceNodes) throw new IllegalArgumentException("Trace requested for a network of " + nodes + " nodes; the limit is " + maxTraceNodes
                    + ". Run without trace, or use the Observatory's team limit.");
        }
        AllocationOutcome o = AllocationEngine.solve(snap, trace);
        List<GreedySummary> gs = new ArrayList<>();
        for (GreedyAllocator.Order ord : GreedyAllocator.Order.values()) {
            var g = GreedyAllocator.allocate(snap, ord);
            gs.add(new GreedySummary(ord.name(), g.allocated(), g.unallocated().size(), g.nanos(), g.compatChecks()));
        }
        return new AllocationRunView(null, null, name, source, o, gs, compare(o, gs));
    }

    static List<String> compare(AllocationOutcome o, List<GreedySummary> gs) {
        List<String> l = new ArrayList<>();
        GreedySummary in = gs.get(0);
        int best = gs.stream().mapToInt(GreedySummary::allocated).max().orElse(0);
        l.add("Edmonds–Karp places " + o.allocated() + " of " + o.totalTeams() + " teams — the proven maximum for this input (maximum-cardinality objective).");
        l.add("First-fit greedy in input order places " + in.allocated() + " (" + (o.allocated() - in.allocated()) + " fewer). Its best ordering here places " + best + ".");
        l.add("Greedy never revisits a decision, so its result depends on team order and is never better than the flow optimum. Equal counts on one dataset do not make greedy optimal in general.");
        l.add("Neither algorithm optimises team preferences or travel distance; only the number of placed teams.");
        return l;
    }

    public List<RunSummary> history() { return runs.list("ALLOCATION"); }
    public JsonNode get(Long id) { return runs.payload(id, "ALLOCATION"); }

    public String exportCsv(Long id) {
        JsonNode o = get(id).path("outcome");
        StringBuilder sb = new StringBuilder("record_type,team,laboratory,slot,category,detail\n");
        for (JsonNode a : o.path("assignments")) sb.append("ASSIGNED,").append(q(a.path("team"))).append(',').append(q(a.path("lab"))).append(',').append(q(a.path("slot"))).append(",,\n");
        for (JsonNode u : o.path("unallocated")) sb.append("UNALLOCATED,").append(q(u.path("team"))).append(",,,").append(q(u.path("category"))).append(',').append(q(u.path("detail"))).append('\n');
        return sb.toString();
    }
    private static String q(JsonNode n) {
        String s = n.asText("");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s; // neutralise spreadsheet formula injection
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
