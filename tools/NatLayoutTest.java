import io.paimon.fx991.ui.NatModelKt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自然书写「分数堆叠」显示修复的回归护栏。
 *
 * 布局本身是 Compose 渲染，JVM 测不了；这里把**不依赖 intrinsic 测量**这件事在源码层面做死，
 * 并用纯 Kotlin 的 ASCII 投影锁住「多个分数并排仍是横排、嵌套结构不变」。
 */
public class NatLayoutTest {

    static int pass = 0;
    static int fail = 0;

    static final String DIV = "\u00F7";
    static final String SQRT = "\u221A";
    static final String RULE = "\u2500"; // ─

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    static String proj(String expr) {
        return NatModelKt.natAsciiText(expr);
    }

    static String line(String[] ls, int i) {
        return i < ls.length ? ls[i] : "";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 源码护栏：LCD 布局不再依赖 intrinsic / fillMaxWidth ==");

        Path src = Path.of("app", "src", "main", "java", "io", "paimon", "fx991", "ui", "NaturalDisplay.kt");
        String code = Files.readString(src, StandardCharsets.UTF_8);

        check("NaturalDisplay.kt 不含 IntrinsicSize", !code.contains("IntrinsicSize"));
        check("NaturalDisplay.kt 不含 fillMaxWidth", !code.contains("fillMaxWidth"));
        check("分数线/上横线用 drawBehind 自绘（按实测 size.width）", code.contains("drawBehind") && code.contains("size.width"));
        check("LCD 行右对齐用 Box 的 End 对齐（非无限宽约束下的 Arrangement）",
                code.contains("contentAlignment = Alignment.BottomEnd") && !code.contains("Arrangement.End"));
        check("分数宽度由内容自然包裹（自定义 measure 取较大者）", code.contains("maxOf(num.width, den.width)"));

        System.out.println();
        System.out.println("== 结构回归：嵌套场景的 ASCII 投影 ==");

        // ① 多个分数并排（真机报告的 bug 场景）：必须仍是横排 3 行，而不是压成竖排
        String[] two = proj("1" + DIV + "2+3" + DIV + "4").split("\n");
        check("两个分数并排 → 3 行（分子/横线/分母），未压成竖排", two.length == 3);
        check("并排第 1 行同时含两个分子 1 与 3", line(two, 0).contains("1") && line(two, 0).contains("3"));
        check("并排第 2 行含两条分数线与 +", count(line(two, 1), RULE) >= 2 && line(two, 1).contains("+"));
        check("并排第 3 行同时含两个分母 2 与 4", line(two, 2).contains("2") && line(two, 2).contains("4"));

        // ② 单个分数：3 行
        String[] one = proj("58" + DIV + "7").split("\n");
        check("单个分数 → 3 行", one.length == 3 && count(String.join("", one), RULE) >= 1);

        // ③ 分数套分数：内外各一条分数线 → 5 行
        String[] nested = proj("1" + DIV + "2" + DIV + "3").split("\n");
        check("分数套分数 → 5 行、两条分数线", nested.length == 5 && count(String.join("", nested), RULE) == 2);

        // ④ 根号里套分数：根号上横线 + 分数横线
        String all4 = String.join("", proj(SQRT + "(1" + DIV + "2)").split("\n"));
        check("根号里套分数 → 含 √ 且 ≥2 条横线", all4.contains(SQRT) && count(all4, RULE) >= 2);

        // ⑤ 分数里套根号
        String all5 = String.join("", proj(SQRT + "2" + DIV + "3").split("\n"));
        check("分数里套根号 → 含 √ 且 ≥2 条横线", all5.contains(SQRT) && count(all5, RULE) >= 2);

        // ⑥ 上标与底数并排：α 抬升一行
        String[] sup = proj("2\u00B2+3\u00B2").split("\n");
        check("上标与底数并排 → 2 行（指数抬升）", sup.length == 2);

        // ⑦ 超长表达式：解析不崩、投影完整（LCD 侧可横向滚动，不被截断）
        String longExpr = "1" + DIV + "2+3" + DIV + "4+5" + DIV + "6+7" + DIV + "8+9" + DIV + "10+11" + DIV + "12";
        String lp = proj(longExpr);
        check("超长表达式可完整投影（不抛异常、非空）", lp.length() > 0 && lp.contains("11"));
        check("超长表达式仍为 3 行横排", lp.split("\n").length == 3);

        System.out.println();
        System.out.println("== 回归护栏：变量后接 '+' 不被吃掉（v1.6.1-fix-pluskey） ==");
        // 真机 bug：按 x 后按 + 「失灵」——实际是 NatParser.primary() 把标识符当函数名，
        // 用 unary() 吃隐式参数时把 '+' 静默吞掉（x+3 渲染成 x3）。修复：隐式参数不从 PLUS 开始。
        check("x+  → 尾部 '+' 必须显示", proj("x+").equals("x+"));
        check("x+3 → '+' 不丢、3 不成隐式参数", proj("x+3").equals("x+3"));
        check("2x+3=7 主推场景 → 完整显示", proj("2x+3=7").equals("2x+3=7"));
        check("x+1=3 → 完整显示", proj("x+1=3").equals("x+1=3"));
        check("3x\u22122=7 → 完整显示", proj("3x\u22122=7").equals("3x\u22122=7"));
        check("Ans+1 → 寄存器标识符后 '+' 也不丢", proj("Ans+1").equals("Ans+1"));
        check("PreAns+1 → 长标识符后 '+' 不丢", proj("PreAns+1").equals("PreAns+1"));
        check("y+2 → 其它变量同样", proj("y+2").equals("y+2"));
        // 对照组：隐式参数的正常用法不退化
        check("x\u22123 → 负号仍可作隐式参数开头", proj("x\u22123").equals("x\u22123"));
        check("x y → 标识符隐式参数保留", proj("x y").equals("xy"));
        check("sin(2)+3 → 括号函数调用后 '+' 正常", proj("sin(2)+3").equals("sin(2)+3"));
        check("x\u00D73 → '×' 不受影响", proj("x\u00D73").equals("x\u00D73"));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
