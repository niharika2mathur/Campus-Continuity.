package com.campus.continuity.csv;

import java.util.*;

/** Minimal RFC-4180 style CSV reader (quotes, escaped quotes, CRLF, BOM). Tracks physical line numbers for error messages. */
public final class CsvParser {
    private CsvParser() {}

    public record Row(int line, List<String> cells) {}
    public record Table(List<String> headers, List<Row> rows) {}

    public static final int MAX_ROWS = 50_000;

    public static Table parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("The CSV file is empty");
        if (text.charAt(0) == '\uFEFF') text = text.substring(1);
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false, wasQuoted = false;
        int line = 1, startLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; } else inQ = false;
                } else { if (c == '\n') line++; cur.append(c); }
            } else if (c == '"' && cur.length() == 0 && !wasQuoted) { inQ = true; wasQuoted = true; }
            else if (c == ',') { cells.add(cur.toString().trim()); cur.setLength(0); wasQuoted = false; }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                cells.add(cur.toString().trim()); cur.setLength(0); wasQuoted = false;
                if (!(cells.size() == 1 && cells.get(0).isEmpty())) rows.add(new Row(startLine, cells));
                cells = new ArrayList<>();
                line++; startLine = line;
                if (rows.size() > MAX_ROWS + 1) throw new IllegalArgumentException("CSV exceeds " + MAX_ROWS + " rows");
            } else cur.append(c);
        }
        if (inQ) throw new IllegalArgumentException("Malformed CSV: unterminated quoted field starting near line " + startLine);
        cells.add(cur.toString().trim());
        if (!(cells.size() == 1 && cells.get(0).isEmpty())) rows.add(new Row(startLine, cells));
        if (rows.isEmpty()) throw new IllegalArgumentException("The CSV file has no header row");
        List<String> headers = new ArrayList<>();
        for (String h : rows.get(0).cells()) headers.add(h.toLowerCase(Locale.ROOT));
        return new Table(headers, rows.subList(1, rows.size()));
    }
}
