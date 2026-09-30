import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexFunc;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.Cx;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.FourierOps;
import io.paimon.fx991.engine.ResidueResult;
import io.paimon.fx991.engine.SolveResult;
import io.paimon.fx991.engine.Unified;
import io.paimon.fx991.engine.UserFnDef;
import io.paimon.fx991.engine.UserFunctions;
import io.paimon.fx991.engine.Value;

/**
 * 批次 G 回归：GeoGebra 式自定义函数（定义/调用/求导/管理/容错）+
 * 傅里叶级数（系数/奇偶/区间校验/部分和）+ 复变函数（求值/留数/围道积分）。
 */
public class GeoCplxFourierTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode RAD = AngleMode.RAD;
    static final AngleMode DEG = AngleMode.DEG;

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 44) + " got=" + got + "  expect=" + expect);
    }

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 44) + " => " + got + (good ? "" : " (expected " + expect + ")"));
    }

    static void check(String what, boolean good) {
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ") + what);
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static CalcValue ev(String expr) {
        return Unified.INSTANCE.evaluate(expr, RAD, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), null, null);
    }

    static String fmt(String expr) {
        try {
            return Unified.INSTANCE.format(ev(expr));
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    static ComplexNum cx(String expr) {
        return Unified.INSTANCE.asComplex(ev(expr));
    }

    static double re(String expr) {
        return cx(expr).getRe().toDouble();
    }

    static double im(String expr) {
        return cx(expr).getIm().toDouble();
    }

    static String errOf(String expr) {
        try {
            ev(expr);
            return "NO-ERROR";
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        // ===================================================================
        // A. GeoGebra 式自定义函数
        // ===================================================================
        System.out.println("== A. GeoGebra 自定义函数 ==");
        UserFunctions.INSTANCE.clear();

        UserFnDef f = UserFunctions.INSTANCE.tryDefine("f(x)=x^2");
        check("A1 定义 f(x)=x^2 成功", f != null && "f(x)".equals(f.signature()));
        eq("A2 f(3) = 9", fmt("f(3)"), "9");
        eq("A3 f(3)+1 = 10", fmt("f(3)+1"), "10");
        check("A4 f(3) 显示为 9（参数绑定为浮点轨，见 README 已知限制）", "9".equals(fmt("f(3)"))
                && Unified.INSTANCE.asValue(ev("f(3)")).getExact() == null);

        UserFunctions.INSTANCE.tryDefine("g(x)=f(x)+1");
        eq("A5 嵌套 g(2)=f(2)+1 = 5", fmt("g(2)"), "5");

        near("A6 f'(2) ≈ 4", re("f'(2)"), 4.0, 1e-6);
        near("A7 f''(2) ≈ 2", re("f''(2)"), 2.0, 1e-4);
        eq("A8 隐式乘法 2f(3) = 18", fmt("2f(3)"), "18");
        eq("A9 隐式乘法 f(2)f(3) = 36", fmt("f(2)f(3)"), "36");
        eq("A10 隐式乘法 3(1+2) = 9（既有行为不退步）", fmt("3(1+2)"), "9");
        eq("A11 隐式乘法 2(1+1)(2+2) = 16", fmt("2(1+1)(2+2)"), "16");

        UserFunctions.INSTANCE.tryDefine("h(t)=t^2+1");
        eq("A12 自由参数名 h(t)=t^2+1, h(3)=10", fmt("h(3)"), "10");

        UserFunctions.INSTANCE.tryDefine("p(x,y)=x+y");
        eq("A13 双参数 p(1,2) = 3", fmt("p(1,2)"), "3");
        eq("A14 参数个数不符报错", errOf("p(1)"), "CalcSyntaxError");
        eq("A15 未定义函数调用报语法错误", errOf("qq(1)"), "CalcSyntaxError");
        check("A16 保留名 sin 不可定义（返回 null 落方程路径）",
                UserFunctions.INSTANCE.tryDefine("sin(x)=x") == null);
        check("A17 nameOk 挡住内置名", !UserFunctions.INSTANCE.nameOk("sin")
                && !UserFunctions.INSTANCE.nameOk("x") && UserFunctions.INSTANCE.nameOk("foo"));

        // 重定义规则：已存在时 '=' 不当定义（留给方程），':=' 强制重定义
        check("A18 已定义时 = 不再当定义", UserFunctions.INSTANCE.tryDefine("f(x)=x^3") == null);
        check("A19 := 强制重定义", UserFunctions.INSTANCE.tryDefine("f(x):=x^3") != null);
        eq("A20 重定义后 f(2) = 8", fmt("f(2)"), "8");

        // 已定义函数参与方程求解（数值路径）
        SolveResult sr = EquationSolver.INSTANCE.solve("f(x)=9", RAD);
        check("A21 f(x)=9 按方程求解有解", sr.getItems().size() >= 1);
        near("A22 f(x)=9 的根 ≈ ∛9", Double.parseDouble(sr.getItems().get(0).getInsert()),
                2.080083823051904, 1e-6);

        // 循环定义被深度护栏挡住（函数名只能是字母：词法与几何软件一致）
        UserFunctions.INSTANCE.tryDefine("v(x)=x+1");
        UserFunctions.INSTANCE.tryDefine("u(x)=v(x)");
        UserFunctions.INSTANCE.tryDefine("v(x):=u(x)");
        eq("A23 循环定义触发递归护栏", errOf("u(1)"), "CalcMathError");

        // 管理：列出 / 删除
        check("A24 代数区列表包含 f/g/h/p", UserFunctions.INSTANCE.list().size() >= 4);
        check("A25 删除函数", UserFunctions.INSTANCE.remove("h") && !UserFunctions.INSTANCE.contains("h"));
        eq("A26 删除后调用报语法错误", errOf("h(3)"), "CalcSyntaxError");

        // 定义外形识别（预览分流）
        check("A27 f(x)=x^2 是定义外形", UserFunctions.INSTANCE.definitionShape("f(x)=x^2") != null);
        check("A28 2x+3=7 不是定义外形", UserFunctions.INSTANCE.definitionShape("2x+3=7") == null);
        check("A29 sin(x)=0.5 不是定义外形（保留名落方程）",
                UserFunctions.INSTANCE.definitionShape("sin(x)=0.5") == null);
        check("A30 定义里含逗号参数外形", UserFunctions.INSTANCE.definitionShape("m(a,b)=a*b") != null);

        UserFunctions.INSTANCE.clear();

        // ===================================================================
        // B. 傅里叶级数
        // ===================================================================
        System.out.println("== B. 傅里叶级数 ==");

        FourierOps.MainArgs fa = FourierOps.INSTANCE.parseMain("fourier(x^2, -pi, pi, 5)");
        check("B1 主行解析 fourier(x^2,-pi,pi,5)", fa != null);
        near("B2 下限 -π", fa.getA(), -Math.PI, 1e-12);
        near("B3 上限 π", fa.getB(), Math.PI, 1e-12);
        check("B4 项数 5", fa.getN() == 5);
        check("B5 非 fourier 串返回 null", FourierOps.INSTANCE.parseMain("x^2+1") == null);

        FourierOps.Result r5 = FourierOps.INSTANCE.compute("x^2", -Math.PI, Math.PI, 5);
        near("B6 a0 = 2π²/3", r5.getA0(), 2.0 * Math.PI * Math.PI / 3.0, 1e-6);
        near("B7 a1 = -4", r5.getAn()[1], -4.0, 1e-6);
        near("B8 a2 = 1", r5.getAn()[2], 1.0, 1e-6);
        near("B9 a3 = -4/9", r5.getAn()[3], -4.0 / 9.0, 1e-6);
        near("B10 a4 = 1/4", r5.getAn()[4], 0.25, 1e-6);
        near("B11 a5 = -4/25", r5.getAn()[5], -0.16, 1e-6);
        check("B12 偶函数 bn 全 0", java.util.Arrays.stream(r5.getBn()).allMatch(v -> v == 0.0));
        check("B13 奇偶识别为偶函数", r5.getParity() == FourierOps.Parity.EVEN);
        check("B14 报告含「偶函数」", FourierOps.INSTANCE.formatReport(r5).contains("偶函数"));
        near("B15 部分和 S5(1) ≈ 1（5 项逼近）", r5.partialAt(1.0), 1.0, 0.06);

        // 奇函数：x³ 在 [-π, π]，bn = 2(−1)ⁿ(6/n³ − π²/n)
        FourierOps.Result rOdd = FourierOps.INSTANCE.compute("x^3", -Math.PI, Math.PI, 3);
        check("B16 奇函数 an 全 0", java.util.Arrays.stream(rOdd.getAn()).allMatch(v -> v == 0.0));
        near("B17 b1 = 2(π²−6)", rOdd.getBn()[1], 2.0 * (Math.PI * Math.PI - 6.0), 1e-5);
        near("B18 b2 = 2(3/4−π²/2)", rOdd.getBn()[2], 2.0 * (0.75 - Math.PI * Math.PI / 2.0), 1e-5);
        near("B19 b3 = 2(π²/3−2/9)", rOdd.getBn()[3], 2.0 * (Math.PI * Math.PI / 3.0 - 2.0 / 9.0), 1e-5);
        check("B20 奇偶识别为奇函数", rOdd.getParity() == FourierOps.Parity.ODD);

        // [0, T] 区间：x 在 [0, 2π]，a0 = 2π，an = 0，bn = −2/n
        FourierOps.Result rT = FourierOps.INSTANCE.compute("x", 0.0, 2.0 * Math.PI, 2);
        near("B21 [0,2π] a0 = 2π", rT.getA0(), 2.0 * Math.PI, 1e-6);
        near("B22 [0,2π] b1 = −2", rT.getBn()[1], -2.0, 1e-6);
        near("B23 [0,2π] b2 = −1", rT.getBn()[2], -1.0, 1e-6);
        check("B24 非对称区间不报奇偶", rT.getParity() == FourierOps.Parity.GENERAL);

        // 部分和表达式可粘回求值（弧度制）
        String sExpr = FourierOps.INSTANCE.partialSumExpr(r5);
        try {
            double v = CalcEngine.INSTANCE.evaluateWith(sExpr, RAD, 1.0, 0.0, 0.0);
            near("B25 部分和表达式可求值且与 partialAt 一致", v, r5.partialAt(1.0), 1e-9);
        } catch (Throwable t) {
            check("B25 部分和表达式可求值（异常：" + t + "）", false);
        }

        // 校验错误
        check("B26 区间反了报错", expectErr("fourier(x, 1, -1, 3)", "区间反了"));
        check("B27 n=0 报错", expectErr("fourier(x, 0, 1, 0)", "正整数"));
        check("B28 区间内无定义报错", expectErr("fourier(1/x, -1, 1, 3)", "无定义"));
        check("B29 参数个数不足报错", expectErr("fourier(x, 0, 1)", "4 个参数"));
        check("B30 端点表达式非法报错", expectErr("fourier(x, 0, zz, 3)", "上限"));

        // ===================================================================
        // C. 复变函数
        // ===================================================================
        System.out.println("== C. 复变函数 ==");

        near("C1 exp(i·π) 实部 ≈ −1", re("exp(i*pi)"), -1.0, 1e-9);
        near("C2 exp(i·π) 虚部 ≈ 0", im("exp(i*pi)"), 0.0, 1e-9);
        near("C3 (1+i)² = 2i 实部", re("(1+i)^2"), 0.0, 1e-9);
        near("C4 (1+i)² = 2i 虚部", im("(1+i)^2"), 2.0, 1e-9);
        eq("C5 abs(3+4i) = 5（复数取模）", fmt("abs(3+4i)"), "5");
        eq("C6 conj(2+3i) = 2 − 3i", fmt("conj(2+3i)"), "2 \u2212 3i");
        near("C7 sin(i) = i·sinh(1)", im("sin(i)"), 1.1752011936438014, 1e-9);
        near("C8 ln(i) = iπ/2（主值分支）", im("ln(i)"), Math.PI / 2.0, 1e-9);
        Unified.INSTANCE.evaluate("ln(i)", RAD, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), null, null);
        check("C9 ln 复数给出分支切割提示", Unified.INSTANCE.getLastNote().contains("主值分支"));
        near("C10 √(4i) 实部 ≈ √2", re("\u221A(4i)"), Math.sqrt(2.0), 1e-9);
        near("C11 √(4i) 虚部 ≈ √2", im("\u221A(4i)"), Math.sqrt(2.0), 1e-9);
        near("C12 (1+i)^(1/2) 模 ≈ 2^(1/4)", cx("(1+i)^(1/2)").modulus(), Math.pow(2.0, 0.25), 1e-8);

        // 复变表达式求值（z 为自变量）
        Cx v1 = ComplexFunc.INSTANCE.evalExpr("z^2+1", Cx.of(1.0, 1.0));
        near("C13 evalExpr (1+i)²+1 实部", v1.getRe(), 1.0, 1e-9);
        near("C14 evalExpr (1+i)²+1 虚部", v1.getIm(), 2.0, 1e-9);
        check("C15 复变表达式拒绝 x 自变量", rejectsX());

        // 留数
        ResidueResult r1 = ComplexFunc.INSTANCE.residueOf("1/(z^2+1)", 0.0, 1.0);
        near("C16 Res(1/(z²+1), i) 实部 ≈ 0", r1.getValue().getRe(), 0.0, 1e-7);
        near("C17 Res(1/(z²+1), i) = −i/2", r1.getValue().getIm(), -0.5, 1e-7);
        check("C18 识别为一阶极点", r1.getOrder() == 1);

        ResidueResult r2 = ComplexFunc.INSTANCE.residueOf("1/z^2", 0.0, 0.0);
        near("C19 Res(1/z², 0) = 0", r2.getValue().modulus(), 0.0, 1e-7);
        check("C20 识别为二阶极点", r2.getOrder() == 2);

        ResidueResult r3 = ComplexFunc.INSTANCE.residueOf("1/z", 0.0, 0.0);
        near("C21 Res(1/z, 0) = 1", r3.getValue().getRe(), 1.0, 1e-8);

        ResidueResult r4 = ComplexFunc.INSTANCE.residueOf("exp(z)/z^3", 0.0, 0.0);
        near("C22 Res(e^z/z³, 0) = 1/2", r4.getValue().getRe(), 0.5, 1e-6);
        check("C23 识别为三阶极点", r4.getOrder() == 3);

        ResidueResult r5c = ComplexFunc.INSTANCE.residueOf("sin(z)/z", 0.0, 0.0);
        check("C24 可去奇点明说「不是极点」", r5c.getOrder() == 0 && r5c.getNote().contains("不是极点"));
        near("C25 可去奇点留数为 0", r5c.getValue().modulus(), 0.0, 1e-8);

        check("C26 本性奇点拒绝（不敢算）", essentialRefused());

        // 主行 res / cint
        near("C27 主行 res(1/(z^2+1), i) = −i/2", im("res(1/(z^2+1), i)"), -0.5, 1e-7);
        Unified.INSTANCE.evaluate("res(1/(z^2+1), i)", RAD, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), null, null);
        check("C28 主行 res 给出阶数附注", Unified.INSTANCE.getLastNote().contains("极点"));

        near("C29 cint(1/z, 0) = 2πi 虚部", im("cint(1/z, 0)"), 2.0 * Math.PI, 1e-6);
        near("C30 cint(1/z, 0) 实部 ≈ 0", re("cint(1/z, 0)"), 0.0, 1e-6);
        near("C31 cint(1/(z^2+1), i, 0-i) = 0（两极点留数相消）",
                cx("cint(1/(z^2+1), i, 0-i)").modulus(), 0.0, 1e-6);
        Unified.INSTANCE.evaluate("cint(1/z, 0)", RAD, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), null, null);
        check("C32 cint 附注含留数定理", Unified.INSTANCE.getLastNote().contains("留数定理"));

        eq("C33 res 参数不足报语法错误", errOf("res(1/z)"), "CalcSyntaxError");
        eq("C34 cint 无极点报语法错误", errOf("cint(1/z)"), "CalcSyntaxError");
        eq("C35 标量轨 i 报数学错误（不进 Unified 时）", scalarIErr(), "CalcMathError");

        // ===================================================================
        System.out.println("RESULT GeoCplxFourierTest pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static boolean expectErr(String src, String keyword) {
        try {
            FourierOps.MainArgs a = FourierOps.INSTANCE.parseMain(src);
            if (a == null) return false;
            FourierOps.INSTANCE.compute(a.getF(), a.getA(), a.getB(), a.getN());
            return false;
        } catch (Throwable t) {
            String m = t.getMessage();
            return m != null && m.contains(keyword);
        }
    }

    static boolean rejectsX() {
        try {
            ComplexFunc.INSTANCE.evalExpr("x+1", Cx.ZERO);
            return false;
        } catch (Throwable t) {
            return t.getClass().getSimpleName().equals("CalcMathError");
        }
    }

    static boolean essentialRefused() {
        try {
            ComplexFunc.INSTANCE.residueOf("exp(1/z)", 0.0, 0.0);
            return false;
        } catch (Throwable t) {
            String m = t.getMessage();
            return t.getClass().getSimpleName().equals("NumericError")
                    && m != null && m.contains("本性奇点");
        }
    }

    static String scalarIErr() {
        try {
            CalcEngine.INSTANCE.evaluateValue("i", RAD, 0.0, 0.0, null, 0.0,
                    new java.util.HashMap<String, Double>());
            return "NO-ERROR";
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }
}
