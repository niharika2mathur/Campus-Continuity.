package com.campus.continuity.csv;

import com.campus.continuity.engine.Model;
import java.util.*;
import java.util.function.Predicate;

/** Row-level validation for the three import types. Pure: no database access (existence checks are injected). */
public final class ImportValidator {
    private ImportValidator() {}

    public record RowResult(int line, String status, List<String> errors, Map<String, String> values) {}
    public record Preview(String type, List<String> fileErrors, List<RowResult> rows, int valid, int invalid, int duplicate) {
        public boolean importable() { return fileErrors.isEmpty() && valid > 0; }
    }

    public static final List<String> LAB_COLS = List.of("code", "name", "building", "capacity", "max_team_size", "equipment", "status", "reason");
    public static final List<String> SLOT_COLS = List.of("code", "label", "day", "start", "duration_minutes", "status");
    public static final List<String> TEAM_COLS = List.of("request_id", "code", "name", "size", "requirements", "duration_minutes", "eligible_slots");
    private static final List<String> LAB_REQ = List.of("code", "capacity");
    private static final List<String> SLOT_REQ = List.of("code", "duration_minutes");
    private static final List<String> TEAM_REQ = List.of("request_id", "code", "size", "duration_minutes");

    public static Preview labs(String csv, Predicate<String> exists) { return run("labs", csv, LAB_COLS, LAB_REQ, exists, null); }
    public static Preview slots(String csv, Predicate<String> exists) { return run("slots", csv, SLOT_COLS, SLOT_REQ, exists, null); }
    public static Preview teams(String csv, Predicate<String> codeExists, Predicate<String> requestIdExists) {
        return run("teams", csv, TEAM_COLS, TEAM_REQ, codeExists, requestIdExists);
    }

    private static Preview run(String type, String csv, List<String> cols, List<String> required,
                               Predicate<String> codeExists, Predicate<String> reqExists) {
        CsvParser.Table t;
        try { t = CsvParser.parse(csv); }
        catch (IllegalArgumentException e) { return new Preview(type, List.of(e.getMessage()), List.of(), 0, 0, 0); }
        List<String> fileErrors = new ArrayList<>();
        for (String r : required) if (!t.headers().contains(r)) fileErrors.add("Missing required column: " + r);
        for (String h : t.headers()) if (!cols.contains(h)) fileErrors.add("Unknown column: " + h + " (allowed: " + String.join(", ", cols) + ")");
        if (!fileErrors.isEmpty()) return new Preview(type, fileErrors, List.of(), 0, 0, 0);
        if (t.rows().isEmpty()) return new Preview(type, List.of("The file contains a header but no data rows"), List.of(), 0, 0, 0);

        List<RowResult> out = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>(), seenReq = new HashSet<>();
        int v = 0, inv = 0, dup = 0;
        for (CsvParser.Row row : t.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < t.headers().size(); i++) m.put(t.headers().get(i), i < row.cells().size() ? row.cells().get(i) : "");
            List<String> errs = new ArrayList<>();
            if (row.cells().size() > t.headers().size()) errs.add("Row has more cells than the header");
            validateFields(type, m, errs);
            String status = errs.isEmpty() ? "VALID" : "INVALID";
            if (errs.isEmpty()) {
                String code = m.get("code").toLowerCase(Locale.ROOT);
                if (!seenCodes.add(code)) { status = "DUPLICATE"; errs.add("Duplicate code within the file: " + m.get("code")); }
                else if (codeExists != null && codeExists.test(m.get("code"))) { status = "DUPLICATE"; errs.add("Code already exists: " + m.get("code")); }
                if (reqExists != null && status.equals("VALID")) {
                    String rid = m.get("request_id");
                    if (!seenReq.add(rid)) { status = "DUPLICATE"; errs.add("Duplicate request_id within the file: " + rid); }
                    else if (reqExists.test(rid)) { status = "DUPLICATE"; errs.add("Request already submitted: " + rid); }
                }
            }
            switch (status) { case "VALID" -> v++; case "DUPLICATE" -> dup++; default -> inv++; }
            out.add(new RowResult(row.line(), status, errs, m));
        }
        return new Preview(type, List.of(), out, v, inv, dup);
    }

    private static void validateFields(String type, Map<String, String> m, List<String> errs) {
        need(m, "code", errs);
        switch (type) {
            case "labs" -> {
                nonNegInt(m, "capacity", errs, true);
                posInt(m, "max_team_size", errs, false);
                enumVal(m, "status", errs);
            }
            case "slots" -> { posInt(m, "duration_minutes", errs, true); enumVal(m, "status", errs); }
            default -> {
                need(m, "request_id", errs);
                posInt(m, "size", errs, true); posInt(m, "duration_minutes", errs, true);
            }
        }
    }

    private static void need(Map<String, String> m, String f, List<String> e) {
        if (m.get(f) == null || m.get(f).isBlank()) e.add(f + " is required");
        else if (m.get(f).length() > 80) e.add(f + " is too long (max 80)");
    }
    private static void posInt(Map<String, String> m, String f, List<String> e, boolean req) { intField(m, f, e, req, 1); }
    private static void nonNegInt(Map<String, String> m, String f, List<String> e, boolean req) { intField(m, f, e, req, 0); }
    private static void intField(Map<String, String> m, String f, List<String> e, boolean req, int min) {
        String s = m.get(f);
        if (s == null || s.isBlank()) { if (req) e.add(f + " is required"); return; }
        try {
            int n = Integer.parseInt(s.trim());
            if (n < min) e.add(f + " must be " + (min == 0 ? "zero or positive (negative capacities are not allowed)" : "at least " + min));
            if (n > 100_000) e.add(f + " is unreasonably large");
        } catch (NumberFormatException ex) { e.add(f + " must be a whole number, got '" + s + "'"); }
    }
    private static void enumVal(Map<String, String> m, String f, List<String> e) {
        String s = m.get(f);
        if (s == null || s.isBlank()) return;
        try { Model.Availability.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { e.add(f + " must be AVAILABLE, DISRUPTED or DEACTIVATED"); }
    }

    /** Splits "a;b;c" (also accepts '|' or '+') into trimmed non-empty tokens. */
    public static List<String> tags(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String x : s.split("[;|+]")) if (!x.isBlank()) out.add(x.trim());
        return out;
    }
}
