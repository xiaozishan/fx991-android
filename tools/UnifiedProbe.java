import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.Unified;
import java.util.HashMap;

public class UnifiedProbe {
    static void run(String label, String expr) {
        try {
            CalcValue v = Unified.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, null, 0.0,
                    new HashMap<String, Double>(), null, null);
            ComplexNum c = Unified.INSTANCE.asComplex(v);
            System.out.println(String.format("%-22s [%s] -> OK %s", label, expr,
                    c == null ? v.toString() : ("re=" + c.getRe().toDouble() + " im=" + c.getIm().toDouble())));
        } catch (Throwable e) {
            System.out.println(String.format("%-22s [%s] -> ERR %s: %s", label, expr,
                    e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    public static void main(String[] a) {
        run("baseline", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0))");
        run("no degree", "sin\u207B\u00B9(1.5\u00D7sin(3.5))");
        run("3.5 degree only", "3.5\u00B0");
        run("sin(3.5 deg)", "sin(3.5\u00B0)");
        run("deg+prime", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0\u2032))");
    }
}
