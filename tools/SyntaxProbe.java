import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.Unified;
import java.util.HashMap;

public class SyntaxProbe {
    static void run(String label, String expr) {
        try {
            CalcValue v = Unified.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, null, 0.0,
                    new HashMap<String, Double>(), null, null);
            ComplexNum c = Unified.INSTANCE.asComplex(v);
            System.out.println(String.format("%-28s [%s] -> OK %s", label, expr,
                    c == null ? v.toString() : ("re=" + c.getRe().toDouble() + " im=" + c.getIm().toDouble())));
        } catch (Throwable e) {
            System.out.println(String.format("%-28s [%s] -> ERR %s: %s", label, expr,
                    e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    public static void main(String[] a) {
        // 图中表达式（减号为 U+2212）
        run("as-shown U+2212", "0.0005\u00D7(5.03/0.02 \u2212 0.03/5.02)");
        run("ascii minus", "0.0005\u00D7(5.03/0.02 - 0.03/5.02)");
        run("ascii times", "0.0005*(5.03/0.02 - 0.03/5.02)");
        run("no outer parens", "0.0005\u00D75.03/0.02-0.03/5.02");
        run("left term", "5.03/0.02");
        run("right term", "0.03/5.02");
        run("outer only", "0.0005\u00D7(5.03/0.02)");
        run("same with 0.5", "0.5\u00D7(5.03/0.02 \u2212 0.03/5.02)");
        run("inner minus", "(251.5 \u2212 0.005976)");
        run("inner parens nested", "0.0005\u00D7((5.03/0.02) \u2212 (0.03/5.02))");
        run("neg after minus", "1-0.03");
        run("neg after minus 2", "5.03/0.02-0.03");
    }
}
