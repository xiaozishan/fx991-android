import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.ComplexRect;
import io.paimon.fx991.engine.Dms;
import io.paimon.fx991.engine.LimitResult;
import io.paimon.fx991.engine.NumericOps;
import io.paimon.fx991.engine.PolarForm;
import io.paimon.fx991.engine.PolarPair;
import io.paimon.fx991.engine.RootResult;
import io.paimon.fx991.engine.Sexagesimal;
import io.paimon.fx991.engine.Unified;

/**
 * 批次 A 数值算法回归：求根 / 定积分 / 数值导数 / 求和 / 极限 /
 * 双曲函数 / 任意底对数 / 任意次根 / 排列组合 / 度分秒 / 极坐标 / GRAD / 显示精度。
 */
public class NumericTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;
    static final AngleMode GRAD = AngleMode.GRAD;

    static final String MUL = "\u00D7";
    static final String DIV = "\u00F7";
    static final String ANGL = "\u2220";

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 40) + " got=" + got + "  expect=" + expect);
    }

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 40) + " => " + got + (good ? "" : " (expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static String fmt(String expr, AngleMode m) {
        try {
            return CalcEngine.INSTANCE.formatValue(
                    CalcEngine.INSTANCE.evaluateValue(expr, m, 0.0, 0.0, null), false, false, 10, null);
        } catch (Throwable e) {
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    /** 统一下面口：∠ 现在是真运算符，可多个 */
    static ComplexNum cx(String expr, AngleMode m) {
        CalcValue v = Unified.INSTANCE.evaluate(expr, m, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), null, null);
        return Unified.INSTANCE.asComplex(v);
    }

    static String errOf(Runnable r) {
        try {
            r.run();
            return "none";
        } catch (Throwable e) {
            return e.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        System.out.println("-- 数值导数：中心差分 + Richardson 外推 --");
        near("d/dx x^2 @3", NumericOps.INSTANCE.derivative("x^2", RAD, 3.0), 6.0, 1e-9);
        near("d/dx x^3 @2", NumericOps.INSTANCE.derivative("x^3", RAD, 2.0), 12.0, 1e-8);
        near("d/dx sin(x) @0 (RAD)", NumericOps.INSTANCE.derivative("sin(x)", RAD, 0.0), 1.0, 1e-9);
        near("d/dx sin(x) @0 (DEG)", NumericOps.INSTANCE.derivative("sin(x)", DEG, 0.0),
            Math.PI / 180.0, 1e-12);
        near("d/dx ln(x) @2", NumericOps.INSTANCE.derivative("ln(x)", RAD, 2.0), 0.5, 1e-9);

        System.out.println("-- 自适应 Simpson 定积分（容差可显式设置） --");
        near("∫x^2 0..1 (tol 1e-9)", NumericOps.INSTANCE.integrate("x^2", RAD, 0, 1, 1e-9), 1.0 / 3.0, 1e-9);
        near("∫x^3 0..2", NumericOps.INSTANCE.integrate("x^3", RAD, 0, 2, 1e-10), 4.0, 1e-10);
        near("∫sin 0..π (RAD)", NumericOps.INSTANCE.integrate("sin(x)", RAD, 0, Math.PI, 1e-10), 2.0, 1e-10);
        near("∫1/(1+x^2) 0..1 = π/4",
                NumericOps.INSTANCE.integrate("1" + DIV + "(1+x^2)", RAD, 0, 1, 1e-12), Math.PI / 4, 1e-10);
        near("∫cos 0..π/2（倒序 a>b 取负）",
                NumericOps.INSTANCE.integrate("cos(x)", RAD, Math.PI / 2, 0, 1e-12), -1.0, 1e-10);

        System.out.println("-- Σ 求和 --");
        near("Σx, 1..100", NumericOps.INSTANCE.summation("x", RAD, 1, 100), 5050.0, 1e-9);
        near("Σx^2, 1..10", NumericOps.INSTANCE.summation("x^2", RAD, 1, 10), 385.0, 1e-9);
        near("Σ1/2^x, 0..10", NumericOps.INSTANCE.summation("1" + DIV + "2^x", RAD, 0, 10),
            1.9990234375, 1e-9);
        eq("下界 > 上界", errOf(() -> NumericOps.INSTANCE.summation("x", RAD, 5, 1)), "NumericError");

        System.out.println("-- SOLVE 数值求根：牛顿法 + 二分法兜底 --");
        RootResult r1 = NumericOps.INSTANCE.solveRoot("x^2-3x+2", RAD, 0.6);
        near("x²-3x+2=0 (x0=0.6)", r1.getRoot(), 1.0, 1e-9);
        eq("根方法", r1.getMethod(), "牛顿法");
        eq("根保留精确分数", r1.getExact() == null ? "null" : r1.getExact().toString(), "1");

        RootResult r2 = NumericOps.INSTANCE.solveRoot("x^2-2", RAD, 1.0);
        near("x²-2=0 (x0=1)", r2.getRoot(), Math.sqrt(2.0), 1e-9);
        eq("√2 保留不了精确分数", r2.getExact() == null ? "null" : r2.getExact().toString(), "null");

        // 初值落在驻点（导数 = 0）→ 牛顿退化 → 退二分
        RootResult r3 = NumericOps.INSTANCE.solveRoot("x^2-2", RAD, 0.0);
        eq("驻点初值退化 → 二分法", r3.getMethod(), "二分法");
        near("二分法仍找到 |x|=√2", Math.abs(r3.getRoot()), Math.sqrt(2.0), 1e-9);

        RootResult r4 = NumericOps.INSTANCE.solveRoot("cos(x)-x", RAD, 1.0);
        near("cos(x)=x（Dottie 数）", r4.getRoot(), 0.7390851332, 1e-9);

        System.out.println("-- Limit 数值极限：左右都给 --");
        LimitResult l1 = NumericOps.INSTANCE.limit("sin(x)" + DIV + "x", RAD, 0.0);
        near("lim x→0 sin(x)/x 左", l1.getLeft(), 1.0, 1e-6);
        near("lim x→0 sin(x)/x 右", l1.getRight(), 1.0, 1e-6);
        eq("左右相等 → equal", String.valueOf(l1.getEqual()), "true");

        LimitResult l2 = NumericOps.INSTANCE.limit("atan(1" + DIV + "x)", RAD, 0.0);
        near("lim x→0⁻ atan(1/x)", l2.getLeft(), -Math.PI / 2, 1e-4);
        near("lim x→0⁺ atan(1/x)", l2.getRight(), Math.PI / 2, 1e-4);
        eq("左右不等 → 不相等", String.valueOf(l2.getEqual()), "false");

        System.out.println("-- 双曲函数 sinh / cosh / tanh + 反双曲 --");
        near("sinh(1)", CalcEngine.INSTANCE.evaluate("sinh(1)", RAD, 0, 0), Math.sinh(1.0), 1e-12);
        near("cosh(2)", CalcEngine.INSTANCE.evaluate("cosh(2)", RAD, 0, 0), Math.cosh(2.0), 1e-12);
        near("tanh(1)", CalcEngine.INSTANCE.evaluate("tanh(1)", RAD, 0, 0), Math.tanh(1.0), 1e-12);
        near("sinh(0)", CalcEngine.INSTANCE.evaluate("sinh(0)", RAD, 0, 0), 0.0, 1e-12);
        near("cosh(0)", CalcEngine.INSTANCE.evaluate("cosh(0)", RAD, 0, 0), 1.0, 1e-12);
        near("asinh(1)", CalcEngine.INSTANCE.evaluate("asinh(1)", RAD, 0, 0),
            Math.log(1 + Math.sqrt(2)), 1e-12);
        near("acosh(2)", CalcEngine.INSTANCE.evaluate("acosh(2)", RAD, 0, 0),
            Math.log(2 + Math.sqrt(3)), 1e-12);
        near("atanh(0.5)", CalcEngine.INSTANCE.evaluate("atanh(0.5)", RAD, 0, 0),
            0.5 * Math.log(3.0), 1e-12);
        eq("acosh(0) 定义域", errOf(() -> CalcEngine.INSTANCE.evaluate("acosh(0)", RAD, 0, 0)),
            "CalcMathError");
        eq("atanh(1) 定义域", errOf(() -> CalcEngine.INSTANCE.evaluate("atanh(1)", RAD, 0, 0)),
            "CalcMathError");

        System.out.println("-- 任意底对数 / 任意次根 / 立方根 / 10ˣ --");
        eq("logb(2,8)", fmt("logb(2,8)", RAD), "3");
        eq("logb(10,1000)", fmt("logb(10,1000)", RAD), "3");
        eq("logb(2,10) 落浮点", fmt("logb(2,10)", RAD), "3.321928095");
        eq("root(3,27)", fmt("root(3,27)", RAD), "3");
        eq("root(2,4)", fmt("root(2,4)", RAD), "2");
        eq("root(2,1" + DIV + "4) 精确分数", fmt("root(2,1" + DIV + "4)", RAD), "1/2");
        eq("root(2,2) 开不尽", fmt("root(2,2)", RAD), "1.414213562");
        eq("root(3,-8)", fmt("root(3,-8)", RAD), "-2");
        eq("cbrt(27)", fmt("cbrt(27)", RAD), "3");
        eq("cbrt(-64)", fmt("cbrt(-64)", RAD), "-4");
        eq("cbrt(2)", fmt("cbrt(2)", RAD), "1.25992105");
        eq("10^3", fmt("10^3", RAD), "1000");
        eq("10^-2", fmt("10^-2", RAD), "1/100");
        eq("root(2,-4) 偶数次负根", errOf(() -> CalcEngine.INSTANCE.evaluateValue("root(2,-4)", RAD, 0.0, 0.0, null)),
            "CalcMathError");

        System.out.println("-- 排列组合：大数不溢出 --");
        eq("npr(5,2)", fmt("npr(5,2)", RAD), "20");
        eq("ncr(5,2)", fmt("ncr(5,2)", RAD), "10");
        eq("npr(10,3)", fmt("npr(10,3)", RAD), "720");
        eq("ncr(52,5)（一手牌）", fmt("ncr(52,5)", RAD), "2598960");
        eq("ncr(100,50) BigInteger",
            NumericOps.INSTANCE.nCr(100L, 50L).toString(),
            "100891344545564193334812497256");
        eq("ncr(100,50) 精确显示（≤128bit）",
            fmt("ncr(100,50)", RAD), "100891344545564193334812497256");
        eq("npr(0,0)", fmt("npr(0,0)", RAD), "1");
        eq("ncr(5,6) 越界", errOf(() -> CalcEngine.INSTANCE.evaluateValue("ncr(5,6)", RAD, 0.0, 0.0, null)),
            "NumericError");

        System.out.println("-- 度分秒 60 进制换算 --");
        near("1°30′30″ → 度", Sexagesimal.INSTANCE.toDegrees(1, 30, 30), 1.5083333333, 1e-9);
        near("0°45′0″ → 度", Sexagesimal.INSTANCE.toDegrees(0, 45, 0), 0.75, 1e-12);
        near("-1°30′ → 度", Sexagesimal.INSTANCE.toDegrees(-1, 30, 0), -1.5, 1e-12);
        Dms d1 = Sexagesimal.INSTANCE.fromDegrees(1.5);
        eq("1.5° → 度分秒", d1.getDeg() + "/" + d1.getMin() + "/" + d1.getSec(), "1/30/0.0");
        Dms d2 = Sexagesimal.INSTANCE.fromDegrees(1.5083333333);
        eq("1.5083333° → 度分秒", d2.getDeg() + "/" + d2.getMin() + "/" + d2.getSec(), "1/30/30.0");
        Dms d3 = Sexagesimal.INSTANCE.fromDegrees(-0.5);
        eq("-0.5° 符号记在 negative 上",
            d3.getNegative() + "/" + d3.getDeg() + "/" + d3.getMin(), "true/0/30");
        eq("Dms 文本带负号", d3.toString(), "\u22120\u00B030\u20320\u2033");
        near("往返一致", Sexagesimal.INSTANCE.toDegrees(
                (double) d2.getDeg(), (double) d2.getMin(), d2.getSec()), 1.5083333333, 1e-8);

        System.out.println("-- 极坐标 ⇄ 直角坐标（复数形式） --");
        ComplexRect c1 = PolarForm.INSTANCE.toRect(2.0, 60.0, DEG);
        near("2∠60° → 实部", c1.getRe(), 1.0, 1e-12);
        near("2∠60° → 虚部", c1.getIm(), Math.sqrt(3), 1e-12);
        ComplexNum c2 = cx("2" + ANGL + "60", DEG);
        near("表达式 2∠60 实部", c2.getRe().toDouble(), 1.0, 1e-12);
        near("表达式 2∠60 虚部", c2.getIm().toDouble(), Math.sqrt(3), 1e-12);
        ComplexNum c3 = cx("(1+2)" + ANGL + "90", DEG);
        near("(1+2)∠90 实部", c3.getRe().toDouble(), 0.0, 1e-12);
        near("(1+2)∠90 虚部", c3.getIm().toDouble(), 3.0, 1e-12);
        // ∠ 提升为真运算符：可多个、可复合
        ComplexNum m1 = cx("9" + ANGL + "60+5" + ANGL + "6", DEG);
        near("9∠60+5∠6 实部", m1.getRe().toDouble(), 9.0 * 0.5 + 5.0 * Math.cos(Math.toRadians(6.0)), 1e-12);
        near("9∠60+5∠6 虚部", m1.getIm().toDouble(), 9.0 * Math.sqrt(3.0) / 2.0 + 5.0 * Math.sin(Math.toRadians(6.0)), 1e-12);
        ComplexNum m2 = cx("2" + ANGL + "30" + MUL + "3", DEG);
        near("2∠30×3 实部", m2.getRe().toDouble(), 3.0 * Math.sqrt(3.0), 1e-12);
        near("2∠30×3 虚部", m2.getIm().toDouble(), 3.0, 1e-12);
        ComplexNum m3 = cx("(1+2)" + ANGL + "90" + "\u2212" + "1", DEG);
        near("(1+2)∠90−1 实部", m3.getRe().toDouble(), -1.0, 1e-12);
        near("(1+2)∠90−1 虚部", m3.getIm().toDouble(), 3.0, 1e-12);
        ComplexNum m4 = cx("1" + ANGL + "100", GRAD);
        near("1∠100(grad) 虚部", m4.getIm().toDouble(), 1.0, 1e-12);
        PolarPair p1 = PolarForm.INSTANCE.toPolar(1.0, Math.sqrt(3), DEG);
        near("直角 → 极坐标 r", p1.getR(), 2.0, 1e-12);
        near("直角 → 极坐标 θ", p1.getTheta(), 60.0, 1e-9);
        PolarPair p2 = PolarForm.INSTANCE.toPolar(-1.0, 0.0, DEG);
        near("(-1,0) → θ=180°", p2.getTheta(), 180.0, 1e-9);
        eq("缺一侧的 ∠ 报语法错误（新语义）",
            errOf(() -> Unified.INSTANCE.evaluate("2" + ANGL, DEG, 0.0, 0.0, null, 0.0,
                    new java.util.HashMap<String, Double>(), null, null)), "CalcSyntaxError");

        System.out.println("-- GRAD 百分度 --");
        near("sin(100 grad)", CalcEngine.INSTANCE.evaluate("sin(100)", GRAD, 0, 0), 1.0, 1e-12);
        near("cos(200 grad)", CalcEngine.INSTANCE.evaluate("cos(200)", GRAD, 0, 0), -1.0, 1e-12);
        near("sin(50 grad) = sin45°", CalcEngine.INSTANCE.evaluate("sin(50)", GRAD, 0, 0),
            Math.sqrt(0.5), 1e-12);
        near("sin⁻¹(1) = 100 grad", CalcEngine.INSTANCE.evaluate("asin(1)", GRAD, 0, 0), 100.0, 1e-9);
        eq("tan(100 grad) 定义域", errOf(() -> CalcEngine.INSTANCE.evaluate("tan(100)", GRAD, 0, 0)),
            "CalcMathError");

        System.out.println("-- 显示精度（有效数字 / 固定小数位） --");
        eq("默认 10 位有效数字", CalcEngine.INSTANCE.format(1.0 / 3.0), "0.3333333333");
        eq("固定 3 位小数", CalcEngine.INSTANCE.format(1.0 / 3.0, 10, 3), "0.333");
        eq("固定 4 位小数", CalcEngine.INSTANCE.format(2.0 / 3.0, 10, 4), "0.6667");
        eq("固定小数位去掉尾零", CalcEngine.INSTANCE.format(2.5, 10, 3), "2.5");
        eq("固定 0 位小数（四舍五入）", CalcEngine.INSTANCE.format(2.6, 10, 0), "3");
        eq("6 位有效数字", CalcEngine.INSTANCE.format(123.456789, 6, null), "123.457");
        eq("FIX 生效时分数让位小数",
            CalcEngine.INSTANCE.formatValue(
                CalcEngine.INSTANCE.evaluateValue("1" + DIV + "3", RAD, 0.0, 0.0, null), false, false, 10, 2),
            "0.33");
        eq("默认仍显示分数",
            CalcEngine.INSTANCE.formatValue(
                CalcEngine.INSTANCE.evaluateValue("1" + DIV + "3", RAD, 0.0, 0.0, null), false, false),
            "1/3");

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
