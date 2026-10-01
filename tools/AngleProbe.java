import io.paimon.fx991.ui.NatModelKt;

/**
 * K3 探针：复现「∠ 一次只能写一个」。
 * 把若干含 ∠ 的表达式做 ASCII 投影，数一数投影里 ∠ 的个数与输入是否一致。
 */
public class AngleProbe {

    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    static void probe(String expr) {
        String p = NatModelKt.natAsciiText(expr);
        int in = count(expr, "\u2220");
        int out = count(p, "\u2220");
        System.out.println("---- 输入: " + expr + "   (输入∠=" + in + ", 投影∠=" + out + (in == out ? "  OK" : "  <<< 丢了!") + ")");
        System.out.println(p);
        System.out.println();
    }

    static void probeCur(String expr, int cursor) {
        // 带光标路径（真机编辑态实际走 buildNatCursor）：把整棵树 ASCII 投影后数 ∠
        String p = io.paimon.fx991.ui.NatModelKt.natAscii(io.paimon.fx991.ui.NatCursorKt.buildNatCursor(expr, cursor)).getLines().stream().reduce((a, b) -> a + "\n" + b).orElse("");
        int in = count(expr, "\u2220");
        int out = count(p, "\u2220");
        System.out.println("---- [cursor=" + cursor + "] " + expr + "   (输入∠=" + in + ", 投影∠=" + out + (in == out ? "  OK" : "  <<< 丢了!") + ")");
    }

    public static void main(String[] args) {
        probe("1\u22202");
        probe("2\u222030\u00D73");
        probe("9\u222060+5\u22206");
        probe("1\u22202+3\u22204+5\u22206");
        probe("(1+2)\u222090\u22121");
        probe("a\u2220b\u2220c");

        // 带光标（编辑态）路径
        probeCur("9\u222060+5\u22206", 8);
        probeCur("9\u222060+5\u22206", 0);
        probeCur("9\u222060+5\u22206", 4);
        probeCur("1\u22202+3\u22204+5\u22206", 12);
    }
}
