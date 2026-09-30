import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.Rational;
import io.paimon.fx991.engine.Value;

/**
 * v2 精确分数双轨 + 自然书写显示的回归测试。
 * 运行：见 tools/run-engine-test-v2.ps1
 */
public class EngineTestV2 {

    static int pass = 0;
    static int fail = 0;

    static final String DIV = "\u00F7";
    static final String MUL = "\u00D7";
    static final String MINUS = "\u2212";
    static final String SQRT = "\u221A";
    static final String SQ2 = "\u00B2";

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 34) + " => " + pad(got, 20) + (good ? "" : "(expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static String fmt(String expr, boolean decimal, boolean mixed) {
        try {
            Value v = CalcEngine.INSTANCE.evaluateValue(expr, AngleMode.DEG, 0.0, 0.0, null);
            return CalcEngine.INSTANCE.formatValue(v, decimal, mixed);
        } catch (Throwable e) {
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        System.out.println("-- 小数字面量 → 有理数 --");
        Rational r = Rational.Companion.fromDecimal("589.3");
        eq("589.3", r.toString(), "5893/10");
        eq("1.52", Rational.Companion.fromDecimal("1.52").toString(), "38/25");
        eq("0.25", Rational.Companion.fromDecimal("0.25").toString(), "1/4");

        System.out.println("-- 参考图那道题：589.3 ÷ 1.52 --");
        eq("精确结果", fmt("589.3" + DIV + "1.52", false, false), "29465/76");
        eq("带分数", fmt("589.3" + DIV + "1.52", false, true), "387 53/76");
        eq("S⇒D 切小数", fmt("589.3" + DIV + "1.52", true, false), "387.6973684");

        System.out.println("-- 有理数精确运算 --");
        eq("1" + DIV + "3", fmt("1" + DIV + "3", false, false), "1/3");
        eq("0.1+0.2", fmt("0.1+0.2", false, false), "3/10");
        eq("1" + DIV + "7+2" + DIV + "7", fmt("1" + DIV + "7+2" + DIV + "7", false, false), "3/7");
        eq("2" + DIV + "3" + MUL + "3" + DIV + "4", fmt("2" + DIV + "3" + MUL + "3" + DIV + "4", false, false), "1/2");
        eq("(1" + DIV + "2)" + SQ2, fmt("(1" + DIV + "2)" + SQ2, false, false), "1/4");
        eq("2^-3", fmt("2^-3", false, false), "1/8");

        System.out.println("-- 能开尽才精确 --");
        eq(SQRT + "4", fmt(SQRT + "4", false, false), "2");
        eq(SQRT + "(1" + DIV + "4)", fmt(SQRT + "(1" + DIV + "4)", false, false), "1/2");
        eq(SQRT + "2（开不尽）", fmt(SQRT + "2", false, false), "1.414213562");
        eq("4^(1" + DIV + "2)", fmt("4^(1" + DIV + "2)", false, false), "2");
        eq("5!", fmt("5!", false, false), "120");

        System.out.println("-- 无理运算落回浮点 --");
        eq("sin(30)", fmt("sin(30)", false, false), "0.5");
        eq("log(100)", fmt("log(100)", false, false), "2");
        eq("ln(e)", fmt("ln(e)", false, false), "1");
        eq("e^2", fmt("e^2", false, false), "7.389056099");
        eq("2" + MUL + "3.141592654…", fmt("2" + MUL + "\u03C0", false, false), "6.283185307");

        System.out.println("-- 规模上限 → 退回小数 --");
        eq("69!", fmt("69!", false, false), "1.711224524" + MUL + "10\u2079\u2078");

        System.out.println("-- 除零 / 错误 --");
        eq("1" + DIV + "0", fmt("1" + DIV + "0", false, false), "ERR:CalcMathError");
        eq("2+", fmt("2+", false, false), "ERR:CalcSyntaxError");

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
