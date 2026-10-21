import io.paimon.fx991.ui.Nat;
import io.paimon.fx991.ui.NatCursorKt;
import io.paimon.fx991.ui.NatModelKt;

/** 打印渲染树，定位「右移一次出现括号」的来源。 */
public class TreeDump {
    static String dump(Nat n, String ind) {
        StringBuilder b = new StringBuilder();
        if (n instanceof Nat.Row) {
            Nat.Row r = (Nat.Row) n;
            b.append(ind).append("Row[").append(r.getItems().size()).append("]\n");
            for (Nat c : r.getItems()) b.append(dump(c, ind + "  "));
        } else if (n instanceof Nat.Frac) {
            Nat.Frac f = (Nat.Frac) n;
            b.append(ind).append("Frac(").append(f.getSrcStart()).append("..").append(f.getSrcEnd()).append(")\n");
            b.append(dump(f.getN(), ind + "  "));
            b.append(dump(f.getD(), ind + "  "));
        } else if (n instanceof Nat.Sup) {
            Nat.Sup s = (Nat.Sup) n;
            b.append(ind).append("Sup(").append(s.getSrcStart()).append("..").append(s.getSrcEnd()).append(")\n");
            b.append(dump(s.getBase(), ind + "  "));
            b.append(dump(s.getExp(), ind + "  "));
        } else if (n instanceof Nat.Sqrt) {
            b.append(ind).append("Sqrt\n");
            b.append(dump(((Nat.Sqrt) n).getA(), ind + "  "));
        } else if (n instanceof Nat.Sym) {
            String t = ((Nat.Sym) n).getText();
            b.append(ind).append("Sym[\"").append(t.isEmpty() ? "<空槽>" : t).append("\"]\n");
        } else if (n instanceof Nat.Cursor) {
            b.append(ind).append("Cursor\n");
        } else {
            b.append(ind).append(n.getClass().getSimpleName()).append("\n");
        }
        return b.toString();
    }

    public static void main(String[] a) {
        String[] exprs = { "6÷5", "6÷5×3" };
        for (String e : exprs) {
            System.out.println("=== " + e + "  (无光标) ===");
            System.out.println(dump(NatModelKt.buildNat(e), ""));
        }
        System.out.println("=== 6÷5 cursor=3 exit=0 ===");
        System.out.println(dump(NatCursorKt.buildNatCursor("6÷5", 3, 0), ""));
        System.out.println("=== 6÷5 cursor=3 exit=1 ===");
        System.out.println(dump(NatCursorKt.buildNatCursor("6÷5", 3, 1), ""));
        System.out.println("=== 6÷5 cursor=0 exit=1 ===");
        System.out.println(dump(NatCursorKt.buildNatCursor("6÷5", 0, 1), ""));
    }
}
