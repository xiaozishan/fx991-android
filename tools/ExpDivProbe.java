import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;

public class ExpDivProbe {
    static void p(String label, String expr, int cur) {
        EditResult r = CursorModel.INSTANCE.insertFraction(expr, cur);
        System.out.println(String.format("%-14s [%s]@%d -> [%s]@%d", label, expr, cur, r.getText(), r.getCursor()));
    }
    public static void main(String[] a) {
        p("e^x", "e^x", 3);
        p("e^11", "e^11", 4);
        p("e^ (empty)", "e^", 2);
        p("2^x+1", "2^x+1", 3);
        p("e^(x)", "e^(x)", 4);
        p("sin(x^2)", "sin(x^2)", 7);
    }
}
