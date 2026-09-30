import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.Matrix;
import io.paimon.fx991.engine.MatrixStore;
import io.paimon.fx991.engine.SolveItem;
import io.paimon.fx991.engine.SolveResult;
import io.paimon.fx991.engine.Unified;
import io.paimon.fx991.engine.Vector3;
import io.paimon.fx991.engine.VectorStore;

/**
 * 统一输入面 + 主行求解回归（REFERENCE 第 6、7 条）。
 *
 * 覆盖：多个 ∠ 真运算符与角度制 · 矩阵/向量/统计/分布在主行直接调用 ·
 * 一元方程（多项式精确根含复根 / 超越方程多根扫描）· 方程组（精确高斯消元）·
 * 无解 / 无穷多解的明确告知。
 */
public class UnifiedTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;
    static final AngleMode GRAD = AngleMode.GRAD;

    static final String ANGL = "\u2220";
    static final String MUL = "\u00D7";
    static final String DIV = "\u00F7";
    static final String MINUS = "\u2212";

    static final MatrixStore MATS = new MatrixStore();
    static final VectorStore VECS = new VectorStore();

    static {
        MATS.set("A", Matrix.ofInts(2, 2, new int[] { 1, 2, 3, 4 }));
        MATS.set("B", Matrix.ofInts(2, 2, new int[] { 2, 0, 1, 2 }));
        MATS.set("C", Matrix.ofInts(3, 3, new int[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 }));
        VECS.set("A", Vector3.ofInts(1, 2, 3));
        VECS.set("B", Vector3.ofInts(4, -5, 6));
    }

    // ---- 断言工具 ----

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " got=" + got + "  expect=" + expect);
    }

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " => " + got + (good ? "" : " (expected " + expect + ")"));
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

    static CalcValue ev(String expr, AngleMode m) {
        return Unified.INSTANCE.evaluate(expr, m, 0.0, 0.0, null, 0.0,
                new java.util.HashMap<String, Double>(), MATS, VECS);
    }

    static String fmt(String expr, AngleMode m) {
        try {
            return Unified.INSTANCE.format(ev(expr, m));
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    static ComplexNum cx(String expr, AngleMode m) {
        return Unified.INSTANCE.asComplex(ev(expr, m));
    }

    static String errOf(String expr, AngleMode m) {
        try {
            ev(expr, m);
            return "NO-ERROR";
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }

    static String solveText(String expr, AngleMode m) {
        try {
            return EquationSolver.INSTANCE.solve(expr, m).getText();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    static SolveResult solve(String expr, AngleMode m) {
        return EquationSolver.INSTANCE.solve(expr, m);
    }

    public static void main(String[] args) {

        // ===================================================================
        System.out.println("-- 任务 1：∠ 作为真运算符（可多个 / 有优先级 / 跟随角度制）--");
        ComplexNum p1 = cx("2" + ANGL + "60", DEG);
        near("2∠60 实部", p1.getRe().toDouble(), 1.0, 1e-12);
        near("2∠60 虚部", p1.getIm().toDouble(), Math.sqrt(3.0), 1e-12);

        ComplexNum p2 = cx("9" + ANGL + "60+5" + ANGL + "6", DEG);
        near("9∠60+5∠6 实部", p2.getRe().toDouble(), 9.0 * 0.5 + 5.0 * Math.cos(Math.toRadians(6.0)), 1e-12);
        near("9∠60+5∠6 虚部", p2.getIm().toDouble(),
                9.0 * Math.sqrt(3.0) / 2.0 + 5.0 * Math.sin(Math.toRadians(6.0)), 1e-12);

        ComplexNum p3 = cx("2" + ANGL + "30" + MUL + "3", DEG);
        near("2∠30×3 实部", p3.getRe().toDouble(), 3.0 * Math.sqrt(3.0), 1e-12);
        near("2∠30×3 虚部", p3.getIm().toDouble(), 3.0, 1e-12);

        ComplexNum p4 = cx("(1+2)" + ANGL + "90" + MINUS + "1", DEG);
        near("(1+2)∠90−1 实部", p4.getRe().toDouble(), -1.0, 1e-12);
        near("(1+2)∠90−1 虚部", p4.getIm().toDouble(), 3.0, 1e-12);

        ComplexNum p5 = cx("1" + ANGL + "100", GRAD);
        near("1∠100（grad）虚部", p5.getIm().toDouble(), 1.0, 1e-12);

        ComplexNum p6 = cx("2" + ANGL + "(π" + DIV + "2)", RAD);
        near("2∠(π÷2)（RAD）虚部", p6.getIm().toDouble(), 2.0, 1e-12);

        ComplexNum p7 = cx("2" + ANGL + "30+2" + ANGL + "150", DEG);
        near("2∠30+2∠150 实部", p7.getRe().toDouble(), 0.0, 1e-12);
        near("2∠30+2∠150 虚部", p7.getIm().toDouble(), 2.0, 1e-12);

        check("复数结果进复数轨", Unified.INSTANCE.isComplex(ev("2" + ANGL + "60", DEG)));
        eq("缺右侧的 ∠ 仍报语法错误", errOf("2" + ANGL, DEG), "CalcSyntaxError");

        // ===================================================================
        System.out.println("-- 任务 2：矩阵 / 向量 主行当值用（不切模式）--");
        eq("det(MatA)", fmt("det(MatA)", DEG), "-2");
        eq("MatA×MatB", fmt("MatA" + MUL + "MatB", DEG), "4  4\n10  8");
        eq("MatA+MatB", fmt("MatA+MatB", DEG), "3  2\n4  6");
        eq("trn(MatA)", fmt("trn(MatA)", DEG), "1  3\n2  4");
        eq("inv(MatA)", fmt("inv(MatA)", DEG), "-2  1\n3/2  -1/2");
        eq("det(MatA×MatB)", fmt("det(MatA" + MUL + "MatB)", DEG), "-8");
        eq("det(MatC)（单位阵）", fmt("det(MatC)", DEG), "1");
        eq("MatA 与标量相乘", fmt("2" + MUL + "MatA", DEG), "2  4\n6  8");
        eq("det(MatA)+1 混合运算", fmt("det(MatA)+1", DEG), "-1");
        eq("未定义矩阵报数学错误", errOf("det(MatD)", DEG), "CalcMathError");

        eq("VctA·VctB（点乘）", fmt("VctA" + "\u00B7" + "VctB", DEG), "12");
        eq("dot(VctA,VctB)", fmt("dot(VctA,VctB)", DEG), "12");
        eq("cross(VctA,VctB)", fmt("cross(VctA,VctB)", DEG), "(27, 6, -13)");
        eq("abs(VctA)", fmt("abs(VctA)", DEG), "3.741657387");
        eq("VctA+VctB", fmt("VctA+VctB", DEG), "(5, -3, 9)");
        eq("VctA 标量乘", fmt("3" + MUL + "VctA", DEG), "(3, 6, 9)");

        // ===================================================================
        System.out.println("-- 任务 2：统计 / 分布 主行当函数调 --");
        // 手写 erf（A&S 7.1.26）本身精度约 1e-7，这里按该精度核
        near("normcdf(0,1,1)", Double.parseDouble(fmt("normcdf(0,1,1)", DEG)), 0.1586552539, 1e-6);
        near("normcdf(1.96)", Double.parseDouble(fmt("normcdf(1.96)", DEG)), 0.9750021049, 1e-6);
        eq("normpdf(0)", fmt("normpdf(0)", DEG), "0.3989422804");
        near("invnorm(0.975)", Double.parseDouble(fmt("invnorm(0.975)", DEG)), 1.959963985, 1e-5);
        eq("mean(1,2,3,4)", fmt("mean(1,2,3,4)", DEG), "2.5");
        eq("sd(1,2,3) 总体σ", fmt("sd(1,2,3)", DEG), "0.8164965809");
        eq("ssd(1,2,3) 样本s", fmt("ssd(1,2,3)", DEG), "1");
        eq("binompdf(10,3,0.5)", fmt("binompdf(10,3,0.5)", DEG), "0.1171875");
        eq("binomcdf(10,3,0.5)", fmt("binomcdf(10,3,0.5)", DEG), "0.171875");
        eq("poissonpdf(2,1)", fmt("poissonpdf(2,1)", DEG), "0.2706705665");
        eq("poissoncdf(2,1)", fmt("poissoncdf(2,1)", DEG), "0.4060058497");
        eq("ncr(100,50) 大数不溢出", fmt("ncr(100,50)", DEG), "100891344545564193334812497256");
        eq("normcdf 参数个数错误", errOf("normcdf(0,1)", DEG), "CalcSyntaxError");

        // ===================================================================
        System.out.println("-- 任务 3：主行直接求解（一元）--");
        eq("2x+3=7", solveText("2x+3=7", DEG), "x = 2");
        eq("x²-3x+2=0", solveText("x\u00B2-3x+2=0", DEG), "x = 1, 2");
        eq("x²+1=0（复根）", solveText("x\u00B2+1=0", DEG), "x = \u00B1i");
        eq("x²-2=0（根式）", solveText("x\u00B2-2=0", DEG), "x = \u00B1\u221A2");
        eq("x²+2x+2=0（复根 p±qi）", solveText("x\u00B2+2x+2=0", DEG), "x = -1 \u00B1 i");
        eq("x³-6x²+11x-6=0", solveText("x\u00B3-6x\u00B2+11x-6=0", DEG), "x = 1, 2, 3");
        eq("2x+3=7（U+2212 减号）", solveText("2x\u22123=7", DEG), "x = 5");

        String sx = solveText("sin(x)=0.5", DEG);
        check("sin(x)=0.5（DEG）含 30°", sx.contains("30\u00B0"));
        check("sin(x)=0.5（DEG）含 150°", sx.contains("150\u00B0"));
        check("sin(x)=0.5（DEG）多根", sx.contains(","));
        String sxr = solveText("sin(x)=0.5", RAD);
        check("sin(x)=0.5（RAD）含 0.5235987756", sxr.contains("0.5235987756"));

        SolveResult r1 = solve("2x+3=7", DEG);
        eq("解①可插入主行", r1.getItems().get(0).getInsert(), "2");
        eq("解的标签", r1.getItems().get(0).getLabel(), "x = 2");
        check("一元解进历史（item 非空）", !r1.getItems().isEmpty());

        // ===================================================================
        System.out.println("-- 任务 3：方程组 / 无解 / 无穷多解 --");
        eq("2x+y=5, x-y=1", solveText("2x+y=5,x-y=1", DEG), "x = 2, y = 1");
        eq("分号分隔", solveText("2x+y=5; x-y=1", DEG), "x = 2, y = 1");
        eq("3x+2y-z=4,2x-y+z=3,x+y+z=6", solveText("3x+2y-z=4,2x-y+z=3,x+y+z=6", DEG),
                "x = 1, y = 2, z = 3");
        eq("x+1=x+2 → 无解", solveText("x+1=x+2", DEG), "\u65E0\u89E3");
        eq("2x=2x → 无穷多解", solveText("2x=2x", DEG), "\u65E0\u7A77\u591A\u89E3");
        eq("x+y=3 → 无穷多解", solveText("x+y=3", DEG), "\u65E0\u7A77\u591A\u89E3");
        eq("x+y=1,x+y=2 → 无解", solveText("x+y=1,x+y=2", DEG), "\u65E0\u89E3");
        eq("无解类型", solve("x+1=x+2", DEG).getKind().toString(), "NONE");
        eq("无穷多解类型", solve("2x=2x", DEG).getKind().toString(), "INFINITE");
        eq("方程组求解类型", solve("2x+y=5,x-y=1", DEG).getKind().toString(), "SOLUTIONS");

        check("looksLikeEquation", EquationSolver.INSTANCE.looksLikeEquation("2x+3=7"));
        check("非方程不误判", !EquationSolver.INSTANCE.looksLikeEquation("2+3"));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
