import io.paimon.fx991.ui.NatModelKt;
import io.paimon.fx991.ui.NatCursorKt;

public class AngleProbe2 {
    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }
    static String flat(io.paimon.fx991.ui.Nat n) {
        return NatModelKt.natAscii(n).getLines().stream().reduce((a,b)->a+"\n"+b).orElse("");
    }
    static void probeAll(String expr) {
        int in = count(expr, "\u2220");
        // 1) 纯投影
        String p = NatModelKt.natAsciiText(expr);
        if (count(p, "\u2220") != in) System.out.println("PROJ-LOSS: " + expr);
        // 2) 每个光标位置
        for (int c = 0; c <= expr.length(); c++) {
            String pc = flat(NatCursorKt.buildNatCursor(expr, c));
            if (count(pc, "\u2220") != in) System.out.println("CURSOR-LOSS: " + expr + " @cursor=" + c + " -> " + pc.replace("\n","|"));
        }
        // 3) natLinear 往返
        String lin = NatCursorKt.natLinearOf(expr);
        if (count(lin, "\u2220") != in) System.out.println("LINEAR-LOSS: " + expr + " -> " + lin);
    }
    public static void main(String[] a) {
        String[] cases = {
            "1\u22202", "2\u222030\u00D73", "9\u222060+5\u22206", "1\u22202+3\u22204+5\u22206",
            "9\u2220", "9\u222060+5\u2220", "\u2220", "5\u2220", "1\u22202\u22203",
            "x\u22202", "A\u2220B", "sin(30)\u222060", "\u221A2\u222045", "(1+2)\u222090\u22121",
            "2\u2220(30+15)", "1\u22202.5", "0\u22200", "Ans\u222060", "9\u222060\u22125\u22206",
        };
        for (String e : cases) probeAll(e);
        System.out.println("done.");
    }
}
