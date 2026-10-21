import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;

public class ParenProbe {
    static void show(String label, EditResult r) {
        if (r == null) { System.out.println(label + " -> null"); return; }
        System.out.println(label + " -> \"" + r.getText() + "\" @" + r.getCursor());
    }

    public static void main(String[] a) {
        String[] exprs = { "6÷5", "(6÷5", "6÷5)", "2^6÷5" };
        for (String e : exprs) {
            show("exitSupIfAtEnd(\"" + e + "\", len)", CursorModel.INSTANCE.exitSupIfAtEnd(e, e.length()));
        }
        show("insertFraction(\"6÷5\", 3)", CursorModel.INSTANCE.insertFraction("6÷5", 3));
        show("insertOperator(\"6÷5\",3,÷)", CursorModel.INSTANCE.insertOperator("6÷5", 3, "÷"));
        System.out.println("autoClose(6÷5) = " + io.paimon.fx991.engine.CalcEngine.INSTANCE.autoClose("6÷5"));
        System.out.println("autoClose((6÷5) = " + io.paimon.fx991.engine.CalcEngine.INSTANCE.autoClose("(6÷5"));
    }
}
