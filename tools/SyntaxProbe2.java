import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.Unified;
import java.util.HashMap;

public class SyntaxProbe2 {
    static void run(String label, String expr) {
        try {
            CalcValue v = Unified.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, null, 0.0,
                    new HashMap<String, Double>(), null, null);
            ComplexNum c = Unified.INSTANCE.asComplex(v);
            System.out.println(String.format("%-24s -> OK %s", label,
                    c == null ? v.toString() : ("re=" + c.getRe().toDouble())));
        } catch (Throwable e) {
            String m = e.getMessage();
            System.out.println(String.format("%-24s -> ERR %s: %s", label,
                    e.getClass().getSimpleName(), m == null ? "" : m.replace("\n", " ")));
        }
    }

    public static void main(String[] a) {
        String base = "0.0005\u00D7(5.03/0.02 \u2212 0.03/5.02)";
        run("plain (control)", base);
        run("nbsp around minus", "0.0005\u00D7(5.03/0.02\u00A0\u2212\u00A00.03/5.02)");
        run("thin space", "0.0005\u00D7(5.03/0.02\u2009\u2212\u20090.03/5.02)");
        run("en dash", "0.0005\u00D7(5.03/0.02 \u2013 0.03/5.02)");
        run("ascii hyphen", "0.0005\u00D7(5.03/0.02 - 0.03/5.02)");
        run("fraction slash", "0.0005\u00D7(5.03\u20440.02 \u2212 0.03\u20445.02)");
        run("space before paren", "0.0005 \u00D7 (5.03/0.02 \u2212 0.03/5.02)");
        run("scientific 5E-4", "5E-4\u00D7(5.03/0.02 \u2212 0.03/5.02)");
        run("fullwidth paren", "0.0005\u00D7\uFF085.03/0.02 \u2212 0.03/5.02\uFF09");
        run("extra dot", "0.0005.\u00D7(5.03/0.02 \u2212 0.03/5.02)");
        run("trailing dot", "0.0005\u00D7(5.03/0.02 \u2212 0.03/5.02).");
        run("double times", "0.0005\u00D7\u00D7(5.03/0.02 \u2212 0.03/5.02)");
        run("extra rp", "0.0005\u00D7(5.03/0.02 \u2212 0.03/5.02))");
        run("extra lp", "0.0005\u00D7((5.03/0.02 \u2212 0.03/5.02)");
    }
}
