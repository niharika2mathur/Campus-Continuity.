package com.campus.continuity.csv;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.Set;

/** CSV parsing and validation tests (CSV-1 … CSV-8). */
class CsvTest {
    @Test @DisplayName("CSV-1 quoted commas, escaped quotes, CRLF and BOM are parsed")
    void parsing() {
        var t = CsvParser.parse("\uFEFFcode,name\r\nA1,\"Lab, North\"\r\nA2,\"He said \"\"hi\"\"\"\r\n");
        assertEquals(2, t.rows().size()); assertEquals("Lab, North", t.rows().get(0).cells().get(1));
        assertEquals("He said \"hi\"", t.rows().get(1).cells().get(1));
    }
    @Test @DisplayName("CSV-2 INVALID: empty file and unterminated quote rejected")
    void malformed() {
        assertThrows(IllegalArgumentException.class, () -> CsvParser.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> CsvParser.parse("a,b\n\"oops,1"));
    }
    @Test @DisplayName("CSV-3 missing required column and unknown column reported as file errors")
    void columns() {
        var p = ImportValidator.labs("code,colour\nA,red\n", c -> false);
        assertFalse(p.fileErrors().isEmpty()); assertFalse(p.importable());
    }
    @Test @DisplayName("CSV-4 negative capacity and non-numeric values are INVALID with messages")
    void numbers() {
        var p = ImportValidator.labs("code,capacity,max_team_size\nA,-3,4\nB,x,4\nC,5,4\n", c -> false);
        assertEquals(2, p.invalid()); assertEquals(1, p.valid());
        assertTrue(p.rows().get(0).errors().get(0).contains("negative"));
    }
    @Test @DisplayName("CSV-5 duplicates inside the file and against existing data are flagged DUPLICATE")
    void duplicates() {
        var p = ImportValidator.teams("request_id,code,size,duration_minutes\nR1,T1,3,60\nR1,T2,3,60\nR3,T1,3,60\nR4,T4,3,60\nR5,T5,3,60\n",
                c -> c.equals("T5"), Set.of("R4")::contains);
        assertEquals(1, p.valid()); assertEquals(4, p.duplicate());
    }
    @Test @DisplayName("CSV-6 BOUNDARY: header-only file is not importable")
    void headerOnly() { assertFalse(ImportValidator.slots("code,duration_minutes\n", c -> false).importable()); }
    @Test @DisplayName("CSV-7 status must be a known availability value")
    void status() { assertEquals(1, ImportValidator.slots("code,duration_minutes,status\nS1,60,BROKEN\n", c -> false).invalid()); }
    @Test @DisplayName("CSV-8 tag lists accept ; | + separators")
    void tags() { assertEquals(3, ImportValidator.tags("gpu; linux|matlab").size()); assertTrue(ImportValidator.tags("").isEmpty()); }
}
