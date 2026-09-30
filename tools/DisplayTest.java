import io.paimon.fx991.ui.NatModelKt;

/**
 * 自然书写 LCD 的布局投影自测（纯 Kotlin，无 Compose 依赖）。
 * 证明：分数上下堆叠、√ 带上横线、上标指数。
 */
public class DisplayTest {

    static int pass = 0;
    static int fail = 0;

    static final String DIV = "\u00F7";

    static void dump(String expr) {
        System.out.println("  expr: " + expr);
        for (String line : NatModelKt.natAsciiText(expr).split("\n")) {
            System.out.println("    |" + line + "|");
        }
        System.out.println();
    }

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    public static void main(String[] args) {
        System.out.println("== 自然书写布局投影 ==");
        dump("589.3" + DIV + "1.52");
        dump("29465/76");
        dump("1" + DIV + "3");
        dump("\u221A(1" + DIV + "4)");
        dump("2\u00B2");
        dump("1\u00D710\u00B9\u2070");
        dump("1" + DIV + "7+2" + DIV + "7");

        System.out.println("== 结构断言 ==");
        // 分数：三行（分子/横线/分母）
        String frac = NatModelKt.natAsciiText("589.3" + DIV + "1.52");
        String[] fl = frac.split("\n");
        check("分数为 3 行（分子 横线 分母）", fl.length == 3);
        check("第二行是分数横线", fl[1].contains("\u2500"));
        check("分子含 589.3", fl[0].contains("589.3"));
        check("分母含 1.52", fl[2].contains("1.52"));

        // 分数结果是 29465 / 76
        String[] rl = NatModelKt.natAsciiText("29465/76").split("\n");
        check("结果分子 29465", rl[0].trim().equals("29465"));
        check("结果分母 76", rl[2].trim().equals("76"));

        // 根号带上横线
        String[] sl = NatModelKt.natAsciiText("\u221A(1" + DIV + "4)").split("\n");
        boolean hasOverline = false;
        for (String l : sl) if (l.contains("\u2500")) hasOverline = true;
        check("根号内容有上横线", hasOverline);
        boolean hasRadical = false;
        for (String l : sl) if (l.contains("\u221A")) hasRadical = true;
        check("有根号符号", hasRadical);

        // 上标：2² 中 2 比基数高一行
        String[] pl = NatModelKt.natAsciiText("2\u00B2").split("\n");
        check("上标占两行（指数抬升）", pl.length == 2);

        // 批次 A：双参数函数的逗号、极坐标的 ∠ 要能画出来（不能被当成非法字符丢掉）
        String[] cl = NatModelKt.natAsciiText("logb(2,8)").split("\n");
        boolean hasComma = false;
        for (String l : cl) if (l.contains(",")) hasComma = true;
        check("双参数函数的逗号能渲染", hasComma);

        String[] al = NatModelKt.natAsciiText("2\u222060").split("\n");
        boolean hasAngle = false;
        for (String l : al) if (l.contains("\u2220")) hasAngle = true;
        check("极坐标 ∠ 能渲染", hasAngle);

        String[] rl2 = NatModelKt.natAsciiText("root(3,27)").split("\n");
        StringBuilder joined = new StringBuilder();
        for (String l : rl2) joined.append(l);
        check("root(3,27) 完整投影", joined.toString().contains("root") &&
                joined.toString().contains("3") && joined.toString().contains("27"));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
