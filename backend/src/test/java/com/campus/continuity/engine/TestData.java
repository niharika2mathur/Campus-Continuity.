package com.campus.continuity.engine;

import com.campus.continuity.engine.Model.*;
import java.util.*;

final class TestData {
    private TestData() {}
    static Lab lab(String c, int cap, int max, String... eq) { return new Lab(c, c, cap, max, Set.of(eq), Availability.AVAILABLE, null); }
    static Slot slot(String c) { return new Slot(c, c, 120, Availability.AVAILABLE, null); }
    static Team team(String c, int size, int dur, String[] req, String... elig) { return new Team(c, c, size, Set.of(req), dur, Set.of(elig)); }
    static Team team(String c, String... elig) { return team(c, 3, 60, new String[0], elig); }
}
