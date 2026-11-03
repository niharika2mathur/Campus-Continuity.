package com.campus.continuity.service;

import com.campus.continuity.domain.Team;
import com.campus.continuity.repo.Repos.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AdminService {
    public record Health(String status, long labs, long slots, long teams, long requestLogEntries, int bloomBits, long bloomInsertions) {}

    private final LabRepo labs; private final SlotRepo slots; private final TeamRepo teams; private final RequestLogRepo logs;
    private final RunRepo runs; private final ActivityRepo activity; private final BloomScreeningService bloom; private final ImportService imports;

    public AdminService(LabRepo labs, SlotRepo slots, TeamRepo teams, RequestLogRepo logs, RunRepo runs, ActivityRepo activity, BloomScreeningService bloom, ImportService imports) {
        this.labs = labs; this.slots = slots; this.teams = teams; this.logs = logs; this.runs = runs; this.activity = activity; this.bloom = bloom; this.imports = imports;
    }

    /** The live Bloom filter is rebuilt from the authoritative store whenever the application starts. */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildBloom() { bloom.rebuild(teams.findAll().stream().map(t -> t.requestId).toList()); }

    @Transactional
    public void reset() {
        logs.deleteAllInBatch(); runs.deleteAllInBatch(); activity.deleteAllInBatch();
        teams.deleteAllInBatch(); labs.deleteAllInBatch(); slots.deleteAllInBatch();
        bloom.rebuild(List.of());
    }

    /** Loads the bundled sample CSVs through the normal import path (validation + Bloom screening). */
    public Map<String, Object> seed(boolean reset) {
        if (reset) reset();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String type : List.of("labs", "slots", "teams")) out.put(type, imports.commit(type, resource("sample-data/" + type + ".csv"), "SEED"));
        return out;
    }

    private static String resource(String path) {
        try { return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException("Bundled sample data missing: " + path, e); }
    }

    public Health health() {
        var st = bloom.state();
        return new Health("UP", labs.count(), slots.count(), teams.count(), logs.count(), st.bits(), st.insertions());
    }
}
