package com.campus.continuity.service;

import com.campus.continuity.dto.Dtos.TraceRequest;
import com.campus.continuity.engine.Model.Snapshot;
import com.campus.continuity.service.AllocationService.AllocationRunView;
import com.campus.continuity.sim.PresetInstances;
import org.springframework.stereotype.Service;
import java.util.List;

/** Produces the real Edmonds–Karp execution trace that drives the Algorithm Observatory. Presets are only inputs. */
@Service
public class ObservatoryService {
    public record PresetInfo(String id, String title, String description, int teams, int labs, int slots) {}

    private final CatalogService catalog; private final AllocationService allocation;
    public ObservatoryService(CatalogService catalog, AllocationService allocation) { this.catalog = catalog; this.allocation = allocation; }

    public List<PresetInfo> presets() {
        return PresetInstances.all().stream().map(p -> new PresetInfo(p.id(), p.title(), p.description(), p.snapshot().teams().size(), p.snapshot().labs().size(), p.snapshot().slots().size())).toList();
    }

    public AllocationRunView trace(TraceRequest req) {
        String src = req.source();
        if (src.startsWith("PRESET:")) {
            var p = PresetInstances.get(src.substring(7));
            return allocation.evaluate(p.snapshot(), p.title(), src, true);
        }
        if (!src.equals("LIVE")) throw new IllegalArgumentException("source must be LIVE or PRESET:<id>");
        Snapshot full = catalog.snapshot();
        int n = Math.min(req.maxTeams() == null ? 12 : req.maxTeams(), full.teams().size());
        Snapshot sub = new Snapshot(full.labs(), full.slots(), full.teams().subList(0, n));
        return allocation.evaluate(sub, "Live data — first " + n + " teams", src, true);
    }
}
