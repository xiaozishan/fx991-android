import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;

public class SinInvProbe2 {
    static void run(String label, String expr) {
        String ac = CalcEngine.INSTANCE.autoClose(expr);
        String res;
        try {
            double v = CalcEngine.INSTANCE.evaluate(ac, AngleMode.DEG, 0.0, 0.0);
            res = "OK  " + CalcEngine.INSTANCE.format(v);
        } catch (Throwable e) {
            res = "ERR " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println(String.format("%-28s raw=[%s] closed=[%s] -> %s", label, expr, ac, res));
    }

    public static void main(String[] a) {
        String S = "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0))";
        run("baseline", S);
        run("deg twice (deg+prime)", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0\u2032))");
        run("deg+prime+sec", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0\u2032\u2033))");
        run("missing one paren", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0)");
        run("missing two parens", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0");
        run("degree no minutes digits", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0 )");
        run("plain parens", "sin\u207B\u00B9(1.5\u00D7sin(3.5))");
        run("degree at end only", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0))");
        run("fullwidth close", "sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0\uFF09)");
    }
}
