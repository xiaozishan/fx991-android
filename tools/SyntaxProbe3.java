import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.Unified;
import java.util.HashMap;

public class SyntaxProbe3 {
    static void run(String label, String expr) {
        try {
            CalcValue v = Unified.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, null, 0.0,
                    new HashMap<String, Double>(), null, null);
            System.out.println(String.format("%-26s -> OK %s", label, v.toString()));
        } catch (Throwable e) {
            System.out.println(String.format("%-26s -> ERR %s: %s", label,
                    e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    public static void main(String[] a) {
        String body = "0.0005\u00D7(5.03/0.02 \u2212 0.03/5.02)";
        run("control", body);
        run("zwsp trailing", body + "\u200B");
        run("zwsp inside", "0.0005\u00D7(5.03/0.02 \u2212\u200B 0.03/5.02)");
        run("nbsp trailing", body + "\u00A0");
        run("cr trailing", body + "\r");
        run("soft hyphen", "0.0005\u00D7(5.03/0.02 \u00AD\u2212 0.03/5.02)");
        run("minus u2010", "0.0005\u00D7(5.03/0.02 \u2010 0.03/5.02)");
        run("minus u2011", "0.0005\u00D7(5.03/0.02 \u2011 0.03/5.02)");
        run("minus u2012", "0.0005\u00D7(5.03/0.02 \u2012 0.03/5.02)");
        run("minus u2015", "0.0005\u00D7(5.03/0.02 \u2015 0.03/5.02)");
        run("minus uFF0D", "0.0005\u00D7(5.03/0.02 \uFF0D 0.03/5.02)");
        run("times u2715", "0.0005\u2715(5.03/0.02 \u2212 0.03/5.02)");
        run("times letter x", "0.0005x(5.03/0.02 \u2212 0.03/5.02)");
    }
}
