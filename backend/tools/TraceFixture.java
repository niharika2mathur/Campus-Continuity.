import com.campus.continuity.engine.*;
import com.campus.continuity.sim.PresetInstances;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Dev tool: serialises a REAL engine trace to JSON so frontend tests render genuine backend output (no hand-written data).
 *  Usage: java -cp <classes> TraceFixture <presetId> <outFile> */
public class TraceFixture {
    static void w(Object o, StringBuilder sb) throws Exception {
        if (o == null) sb.append("null");
        else if (o instanceof String s) sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        else if (o instanceof Number || o instanceof Boolean) sb.append(o);
        else if (o instanceof Enum<?> e) sb.append('"').append(e.name()).append('"');
        else if (o instanceof int[] a) { sb.append('['); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } sb.append(']'); }
        else if (o instanceof boolean[] a) { sb.append('['); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } sb.append(']'); }
        else if (o instanceof List<?> l) { sb.append('['); boolean f = true; for (Object x : l) { if (!f) sb.append(','); f = false; w(x, sb); } sb.append(']'); }
        else if (o.getClass().isRecord()) {
            sb.append('{'); boolean f = true;
            for (RecordComponent rc : o.getClass().getRecordComponents()) {
                if (!f) sb.append(','); f = false;
                sb.append('"').append(rc.getName()).append("\":");
                Method m = rc.getAccessor(); m.setAccessible(true); w(m.invoke(o), sb);
            }
            sb.append('}');
        } else throw new IllegalArgumentException(o.getClass().toString());
    }
    public static void main(String[] a) throws Exception {
        var p = PresetInstances.get(a[0]);
        var outcome = AllocationEngine.solve(p.snapshot(), true);
        StringBuilder sb = new StringBuilder();
        w(outcome, sb);
        Files.writeString(Path.of(a[1]), "{\"outcome\":" + sb + "}");
    }
}
