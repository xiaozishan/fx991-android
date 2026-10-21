import io.paimon.fx991.ui.NatCursorKt;
import io.paimon.fx991.ui.NatModelKt;
import io.paimon.fx991.ui.Nat;

public class PathProbe {
    static void p(String expr, int cur) {
        Nat n = NatCursorKt.buildNatCursor(expr, cur);
        String path = NatCursorKt.natCursorPath(n);
        System.out.println(String.format("%-12s @%d -> %s", expr, cur, path.isEmpty() ? "(none)" : path));
    }
    public static void main(String[] a) {
        String e = "int(,,)";
        for (int i = 3; i <= 7; i++) p(e, i);
        System.out.println("---");
        String f = "int(x,0,1)";
        for (int i = 4; i <= 10; i++) p(f, i);
    }
}
