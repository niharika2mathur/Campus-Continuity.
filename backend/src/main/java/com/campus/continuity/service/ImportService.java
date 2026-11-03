package com.campus.continuity.service;

import com.campus.continuity.csv.ImportValidator;
import com.campus.continuity.csv.ImportValidator.Preview;
import com.campus.continuity.csv.ImportValidator.RowResult;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.Model.Availability;
import com.campus.continuity.repo.Repos.*;
import com.campus.continuity.web.ApiExceptions.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** CSV import: preview (validation only, no writes) and commit (valid rows only; team rows go through Bloom screening). */
@Service
public class ImportService {
    private final LabRepo labs; private final SlotRepo slots; private final TeamRepo teams;
    private final CatalogService catalog; private final TeamService teamService; private final ActivityService activity;

    public ImportService(LabRepo labs, SlotRepo slots, TeamRepo teams, CatalogService catalog, TeamService teamService, ActivityService activity) {
        this.labs = labs; this.slots = slots; this.teams = teams; this.catalog = catalog; this.teamService = teamService; this.activity = activity;
    }

    public static String read(MultipartFile f) {
        if (f == null || f.isEmpty()) throw new IllegalArgumentException("No file uploaded or the file is empty");
        try { return new String(f.getBytes(), StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalArgumentException("Could not read the uploaded file"); }
    }

    public Preview preview(String type, String csv) {
        return switch (type) {
            case "labs" -> ImportValidator.labs(csv, labs::existsByCodeIgnoreCase);
            case "slots" -> ImportValidator.slots(csv, slots::existsByCodeIgnoreCase);
            case "teams" -> ImportValidator.teams(csv, teams::existsByCodeIgnoreCase, id -> teams.existsByRequestId(TeamService.normalizeRequestId(id)));
            default -> throw new IllegalArgumentException("Unknown import type '" + type + "' (use labs, slots or teams)");
        };
    }

    public ImportResult commit(String type, String csv, String source) {
        Preview p = preview(type, csv);
        if (!p.fileErrors().isEmpty()) throw new IllegalArgumentException(String.join("; ", p.fileErrors()));
        int created = 0, dup = p.duplicate(), invalid = p.invalid(), bloomRejected = 0;
        List<String> messages = new ArrayList<>();
        for (RowResult row : p.rows()) {
            if (!row.status().equals("VALID")) { messages.add("Line " + row.line() + " skipped: " + String.join("; ", row.errors())); continue; }
            Map<String, String> m = row.values();
            try {
                switch (type) {
                    case "labs" -> {
                        var v = catalog.createLab(new LabRequest(m.get("code"), or(m.get("name"), m.get("code")), m.get("building"), Integer.parseInt(m.get("capacity")),
                                m.get("max_team_size") == null || m.get("max_team_size").isBlank() ? 6 : Integer.parseInt(m.get("max_team_size")), ImportValidator.tags(m.get("equipment"))));
                        Availability st = status(m.get("status"));
                        if (st != Availability.AVAILABLE) catalog.setLabStatus(v.id(), new StatusRequest(st, or(m.get("reason"), "Imported as " + st)));
                    }
                    case "slots" -> {
                        var v = catalog.createSlot(new SlotRequest(m.get("code"), or(m.get("label"), m.get("code")), m.get("day"), m.get("start"), Integer.parseInt(m.get("duration_minutes"))));
                        Availability st = status(m.get("status"));
                        if (st != Availability.AVAILABLE) catalog.setSlotStatus(v.id(), new StatusRequest(st, "Imported as " + st));
                    }
                    default -> teamService.register(new TeamRequest(m.get("request_id"), m.get("code"), m.get("name"), Integer.parseInt(m.get("size")),
                            ImportValidator.tags(m.get("requirements")), Integer.parseInt(m.get("duration_minutes")), ImportValidator.tags(m.get("eligible_slots"))), source, false);
                }
                created++;
            } catch (ConflictException e) {
                if (e.getMessage().startsWith("Duplicate request")) bloomRejected++; else dup++;
                messages.add("Line " + row.line() + " rejected: " + e.getMessage());
            } catch (IllegalArgumentException e) { invalid++; messages.add("Line " + row.line() + " invalid: " + e.getMessage()); }
        }
        activity.log("IMPORT", type + " import: " + created + " created, " + dup + " duplicates, " + invalid + " invalid, " + bloomRejected + " Bloom-screened duplicates");
        return new ImportResult(type, created, dup, invalid, bloomRejected, messages.size() > 50 ? messages.subList(0, 50) : messages);
    }

    private static String or(String a, String b) { return a == null || a.isBlank() ? b : a; }
    private static Availability status(String s) { return s == null || s.isBlank() ? Availability.AVAILABLE : Availability.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
}
