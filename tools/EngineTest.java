import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;

/**
 * 引擎行为验证（直接跑在 build 产物 kotlin-classes/debug 上）。
 * 运行见 tools/run-engine-test.ps1
 */
public class EngineTest {

    static int pass = 0;
    static int fail = 0;

    static final String MUL = "\u00D7";   // ×
    static final String DIV = "\u00F7";   // ÷
    static final String MINUS = "\u2212"; // −
    static final String SQ2 = "\u00B2";   // ²
    static final String RECIP = "\u207B\u00B9"; // ⁻¹
    static final String SQRT = "\u221A";  // √
    static final String PI = "\u03C0";    // π
    static final String INV = "\u207B\u00B9";

    static void ok(String expr, AngleMode m, double ans, double mem, String expect) {
        String got;
        try {
            double v = CalcEngine.INSTANCE.evaluate(expr, m, ans, mem);
            got = CalcEngine.INSTANCE.format(v);
        } catch (Throwable e) {
            got = "ERR:" + e.getClass().getSimpleName();
        }
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(expr, 24) + " => " + pad(got, 16) + (good ? "" : "(expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    public static void main(String[] args) {
        AngleMode DEG = AngleMode.DEG;
        AngleMode RAD = AngleMode.RAD;

        System.out.println("-- 四则运算与优先级 --");
        ok("2+3", DEG, 0, 0, "5");
        ok("2+3" + MUL + "4", DEG, 0, 0, "14");
        ok("(2+3)" + MUL + "4", DEG, 0, 0, "20");
        ok("10" + MINUS + "2" + MUL + "3", DEG, 0, 0, "4");
        ok("10" + DIV + "4", DEG, 0, 0, "2.5");
        ok("2" + MUL + "(3+4)", DEG, 0, 0, "14");
        ok("8" + DIV + "2" + DIV + "2", DEG, 0, 0, "2");

        System.out.println("-- 乘方 / 一元负号 --");
        ok("2^3", DEG, 0, 0, "8");
        ok("2^-3", DEG, 0, 0, "0.125");
        ok("2^3^2", DEG, 0, 0, "512");
        ok("-2^2", DEG, 0, 0, "-4");
        ok("2" + SQ2, DEG, 0, 0, "4");
        ok("5" + SQ2, DEG, 0, 0, "25");
        ok("2" + MINUS + "3", DEG, 0, 0, "-1");
        ok("-5+3", DEG, 0, 0, "-2");

        System.out.println("-- 隐式乘法 --");
        ok("2" + PI, DEG, 0, 0, "6.283185307");
        ok("3(4+5)", DEG, 0, 0, "27");
        ok("1" + DIV + "2" + PI, DEG, 0, 0, "0.1591549431");
        ok("2(3)", DEG, 0, 0, "6");
        ok("2e", DEG, 0, 0, "5.436563657");
        ok("(1+1)(2+2)", DEG, 0, 0, "8");

        System.out.println("-- 阶乘 / 百分号 --");
        ok("5!", DEG, 0, 0, "120");
        ok("0!", DEG, 0, 0, "1");
        ok("5%", DEG, 0, 0, "0.05");
        ok("200" + MUL + "5%", DEG, 0, 0, "10");
        ok("100+10%", DEG, 0, 0, "110");
        ok("100" + MINUS + "10%", DEG, 0, 0, "90");

        System.out.println("-- 根号 / 倒数 --");
        ok(SQRT + "9", DEG, 0, 0, "3");
        ok(SQRT + "(9)", DEG, 0, 0, "3");
        ok(SQRT + "9+1", DEG, 0, 0, "4");
        ok("2" + RECIP, DEG, 0, 0, "0.5");
        ok("4" + SQ2 + RECIP, DEG, 0, 0, "0.0625");

        System.out.println("-- 三角函数（DEG） --");
        ok("sin(30)", DEG, 0, 0, "0.5");
        ok("sin(180)", DEG, 0, 0, "0");
        ok("cos(90)", DEG, 0, 0, "0");
        ok("tan(45)", DEG, 0, 0, "1");
        ok("cos(60)", DEG, 0, 0, "0.5");
        ok("sin" + INV + "(0.5)", DEG, 0, 0, "30");
        ok("cos" + INV + "(1)", DEG, 0, 0, "0");
        ok("tan" + INV + "(1)", DEG, 0, 0, "45");

        System.out.println("-- 三角函数（RAD） --");
        ok("sin(0)", RAD, 0, 0, "0");
        ok("cos(0)", RAD, 0, 0, "1");
        ok("sin(" + PI + DIV + "2)", RAD, 0, 0, "1");

        System.out.println("-- 对数 / 指数 / 常量 --");
        ok("log(100)", DEG, 0, 0, "2");
        ok("log(1000)", DEG, 0, 0, "3");
        ok("ln(e)", DEG, 0, 0, "1");
        ok("exp(0)", DEG, 0, 0, "1");
        ok("e^2", DEG, 0, 0, "7.389056099");
        ok("2^10", DEG, 0, 0, "1024");

        System.out.println("-- 记忆 / Ans --");
        ok("Ans+1", DEG, 41, 0, "42");
        ok("M" + MUL + "2", DEG, 0, 7, "14");
        ok("Ans", DEG, 3.5, 0, "3.5");

        System.out.println("-- 精度与显示 --");
        ok("0.1+0.2", DEG, 0, 0, "0.3");
        ok("1" + DIV + "3", DEG, 0, 0, "0.3333333333");
        ok("10^10", DEG, 0, 0, "1" + MUL + "10\u00B9\u2070");
        ok("1" + DIV + "100000000000", DEG, 0, 0, "1" + MUL + "10\u207B\u00B9\u00B9");
        ok("69!", DEG, 0, 0, "1.711224524" + MUL + "10\u2079\u2078");

        System.out.println("-- 错误处理 --");
        ok("1" + DIV + "0", DEG, 0, 0, "ERR:CalcMathError");
        ok("log(0)", DEG, 0, 0, "ERR:CalcMathError");
        ok("ln(-1)", DEG, 0, 0, "ERR:CalcMathError");
        ok("asin(2)", DEG, 0, 0, "ERR:CalcMathError");
        ok("tan(90)", DEG, 0, 0, "ERR:CalcMathError");
        ok("(-3)!", DEG, 0, 0, "ERR:CalcMathError");
        ok(SQRT + "(-1)", DEG, 0, 0, "ERR:CalcMathError");
        ok(SQRT + "(2)" + MINUS + "2", DEG, 0, 0, "-0.5857864376");
        ok("1+2)", DEG, 0, 0, "ERR:CalcSyntaxError");
        ok("2+", DEG, 0, 0, "ERR:CalcSyntaxError");
        ok("", DEG, 0, 0, "ERR:CalcSyntaxError");
        ok("foo(1)", DEG, 0, 0, "ERR:CalcSyntaxError");

        System.out.println("-- 括号自动补全 --");
        System.out.println("  autoClose(\"sin(30\")  = " + CalcEngine.INSTANCE.autoClose("sin(30"));
        System.out.println("  autoClose(\"2+3\")     = " + CalcEngine.INSTANCE.autoClose("2+3"));
        double v = CalcEngine.INSTANCE.evaluate(CalcEngine.INSTANCE.autoClose("sin(30"), DEG, 0, 0);
        boolean ac = CalcEngine.INSTANCE.format(v).equals("0.5");
        if (ac) pass++; else fail++;
        System.out.println((ac ? "  PASS  " : "  FAIL  ") + "sin(30 自动补括号 => " + CalcEngine.INSTANCE.format(v));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
