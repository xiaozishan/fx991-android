import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;

public class SinInvProbe {
    static void run(String expr) {
        try {
            double v = CalcEngine.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0);
            System.out.println("OK   [" + expr + "] = " + CalcEngine.INSTANCE.format(v));
        } catch (Throwable e) {
            System.out.println("ERR  [" + expr + "] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static void main(String[] a) {
        run("sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0))");
        run("sin\u207B\u00B9(1.5\u00D7sin(3.5))");
        run("sin(3.5\u00B0)");
        run("sin\u207B\u00B9(0.5)");
        run("1.5\u00D7sin(3.5\u00B0)");
        run("sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0)");
        System.out.println("autoClose: " + CalcEngine.INSTANCE.autoClose("sin\u207B\u00B9(1.5\u00D7sin(3.5\u00B0))"));
    }
}
