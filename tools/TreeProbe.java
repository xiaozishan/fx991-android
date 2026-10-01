import io.paimon.fx991.ui.Nat;
import io.paimon.fx991.ui.NatModelKt;

/** 调试探针：打印节点树 span */
public class TreeProbe {

    static void dump(Nat n, String indent) {
        String kind = n.getClass().getSimpleName();
        String extra = "";
        if (n instanceof Nat.Sym) extra = " \"" + ((Nat.Sym) n).getText() + "\"";
        System.out.println(indent + kind + extra + "  [" + n.getSrcStart() + "," + n.getSrcEnd() + "]");
        if (n instanceof Nat.Row) for (Nat c : ((Nat.Row) n).getItems()) dump(c, indent + "  ");
        if (n instanceof Nat.Frac) { dump(((Nat.Frac) n).getN(), indent + "  N:"); dump(((Nat.Frac) n).getD(), indent + "  D:"); }
        if (n instanceof Nat.Sqrt) dump(((Nat.Sqrt) n).getA(), indent + "  ");
        if (n instanceof Nat.Sup) { dump(((Nat.Sup) n).getBase(), indent + "  B:"); dump(((Nat.Sup) n).getExp(), indent + "  E:"); }
        if (n instanceof Nat.Integ) { dump(((Nat.Integ) n).getBody(), indent + "  b:"); dump(((Nat.Integ) n).getLo(), indent + "  l:"); dump(((Nat.Integ) n).getHi(), indent + "  h:"); }
    }

    static void show(String expr) {
        System.out.println("=== [" + expr + "]  len=" + expr.length());
        try {
            dump(NatModelKt.buildNat(expr), "");
        } catch (Throwable t) {
            System.out.println("  THROW: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        System.out.println();
    }

    public static void main(String[] args) {
        show("x⁻¹");
        show("x⁻¹+1");
        show("√(x)+1");
        show("√(x)");
    }
}
