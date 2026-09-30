import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.CalcSyntaxError;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.DistrOps;
import io.paimon.fx991.engine.ExactMath;
import io.paimon.fx991.engine.FuncHelp;
import io.paimon.fx991.engine.FuncHelpEntry;
import io.paimon.fx991.engine.LinReg;
import io.paimon.fx991.engine.Matrix;
import io.paimon.fx991.engine.MatrixStore;
import io.paimon.fx991.engine.OneVarStat;
import io.paimon.fx991.engine.PolarPair;
import io.paimon.fx991.engine.StatOps;
import io.paimon.fx991.engine.Value;
import io.paimon.fx991.engine.Vector3;
import io.paimon.fx991.engine.VectorStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批次 C 回归：复数 / 矩阵 / 向量 / 统计与回归 / 概率分布 / 函数帮助。
 * 全部走纯 Kotlin 的 engine 层（可 JVM 直跑）。
 */
public class BatchCTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;
    static final AngleMode GRAD = AngleMode.GRAD;

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
    }

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " got=" + got + "  expect=" + expect);
    }

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static String errOf(Runnable r) {
        try {
            r.run();
            return "none";
        } catch (Throwable e) {
            return e.getClass().getSimpleName();
        }
    }

    static void q(String what, Runnable r, String expect) {
        eq(what, errOf(r), expect);
    }

    static List<Value> vals(String... ss) {
        List<Value> out = new ArrayList<>();
        for (String s : ss) out.add(ComplexNum.literal(s));
        return out;
    }

    public static void main(String[] args) {

        // ===================================================================
        System.out.println("-- 复数：四则（精确分数轨） --");
        eq("(1+2i)+(3−4i)", ComplexNum.ofLiteral("1", "2")
                .plus(ComplexNum.ofLiteral("3", "-4")).format(), "4 \u2212 2i");
        eq("(5+6i)−(1+2i)", ComplexNum.ofLiteral("5", "6")
                .minus(ComplexNum.ofLiteral("1", "2")).format(), "4 + 4i");
        eq("(1+2i)×(3+4i)", ComplexNum.ofLiteral("1", "2")
                .times(ComplexNum.ofLiteral("3", "4")).format(), "-5 + 10i");
        eq("(1/2+1/3i)×(1/2−1/3i) 精确", ComplexNum.ofLiteral("1/2", "1/3")
                .times(ComplexNum.ofLiteral("1/2", "-1/3")).format(), "13/36");
        eq("(1+2i)÷(3+4i) 精确", ComplexNum.ofLiteral("1", "2")
                .div(ComplexNum.ofLiteral("3", "4")).format(), "11/25 + 2/25i");
        eq("(1/2+3/4i)+(1/4+1/4i)", ComplexNum.ofLiteral("1/2", "3/4")
                .plus(ComplexNum.ofLiteral("1/4", "1/4")).format(), "3/4 + 1i");
        eq("除法分母为零报错", errOf(() -> ComplexNum.ofLiteral("1", "1")
                .div(ComplexNum.ofLiteral("0", "0"))), "CalcMathError");
        check("精确轨标记：(1/2+1/3i) 精确", ComplexNum.ofLiteral("1/2", "1/3").getExact());
        check("浮点轨标记：√2 i 不精确", !ComplexNum.of(1.0, Math.sqrt(2.0)).getExact());

        System.out.println("-- 复数：模 / 辐角 / 共轭 --");
        check("|3+4i| 精确 = 5", ComplexNum.ofLiteral("3", "4").modulusExact() != null
                && ComplexNum.ofLiteral("3", "4").modulusExact().toString().equals("5"));
        near("|3+4i| 浮点", ComplexNum.ofLiteral("3", "4").modulus(), 5.0, 1e-12);
        near("|1+i|", ComplexNum.of(1.0, 1.0).modulus(), Math.sqrt(2.0), 1e-12);
        check("|1+i| 无精确值（√2 开不尽）", ComplexNum.ofLiteral("1", "1").modulusExact() == null);
        check("|3/4+  0i| 精确 = 3/4", ComplexNum.ofLiteral("3/4", "0").modulusExact().toString().equals("3/4"));
        near("arg(0+1i) DEG = 90", ComplexNum.of(0.0, 1.0).arg(DEG), 90.0, 1e-9);
        near("arg(1+1i) DEG = 45", ComplexNum.of(1.0, 1.0).arg(DEG), 45.0, 1e-9);
        near("arg(−1+0i) DEG = 180", ComplexNum.of(-1.0, 0.0).arg(DEG), 180.0, 1e-9);
        near("arg(1−1i) DEG = −45", ComplexNum.of(1.0, -1.0).arg(DEG), -45.0, 1e-9);
        near("arg(0+1i) RAD = π/2", ComplexNum.of(0.0, 1.0).arg(RAD), Math.PI / 2.0, 1e-12);
        near("arg(0+1i) GRAD = 100", ComplexNum.of(0.0, 1.0).arg(GRAD), 100.0, 1e-9);
        eq("共轭 (1+2i) → 1−2i", ComplexNum.ofLiteral("1", "2").conjugate().format(), "1 \u2212 2i");
        eq("共轭 (3/4+1/5i)", ComplexNum.ofLiteral("3/4", "1/5").conjugate().format(),
                "3/4 \u2212 1/5i");
        eq("实部显示（纯实数）", ComplexNum.ofLiteral("7", "0").format(), "7");
        check("(1+0i) 判定为实数", ComplexNum.ofLiteral("1", "0").isReal(1e-12));

        System.out.println("-- 复数：直角 ⇄ 极坐标 --");
        PolarPair pp = ComplexNum.of(1.0, 1.0).toPolar(DEG);
        near("(1+1i)→r", pp.getR(), Math.sqrt(2.0), 1e-12);
        near("(1+1i)→θ", pp.getTheta(), 45.0, 1e-9);
        ComplexNum back = ComplexNum.fromPolar(2.0, 60.0, DEG);
        near("r∠θ(2,60)→实部", back.getRe().toDouble(), 1.0, 1e-12);
        near("r∠θ(2,60)→虚部", back.getIm().toDouble(), Math.sqrt(3.0), 1e-12);
        near("r∠θ(3,90)→虚部 3", ComplexNum.fromPolar(3.0, 90.0, DEG).getIm().toDouble(), 3.0, 1e-12);
        near("GrAD r∠θ(1,100)→虚部 1", ComplexNum.fromPolar(1.0, 100.0, GRAD).getIm().toDouble(), 1.0, 1e-12);

        // ===================================================================
        System.out.println("-- 矩阵：加减 / 标量 / 转置 --");
        Matrix ma = Matrix.ofInts(2, 2, new int[]{1, 2, 3, 4});
        Matrix mb = Matrix.ofInts(2, 2, new int[]{5, 6, 7, 8});
        eq("A+B", ma.plus(mb).format(), "6  8\n10  12");
        eq("A−B", ma.minus(mb).format(), "-4  -4\n-4  -4");
        eq("A×3（标量）", ma.scalar(ComplexNum.literal("3")).format(), "3  6\n9  12");
        eq("A×B", ma.times(mb).format(), "19  22\n43  50");
        eq("A+B 维度不符报错",
                errOf(() -> ma.plus(Matrix.ofInts(3, 3, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1}))), "NumericError");
        eq("A×B 维度不符报错",
                errOf(() -> ma.times(Matrix.ofInts(3, 3, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1}))), "NumericError");
        Matrix rect = Matrix.ofInts(2, 3, new int[]{1, 2, 3, 4, 5, 6});
        eq("2×3 转置为 3×2", rect.transpose().format(), "1  4\n2  5\n3  6");
        check("转置后尺寸", rect.transpose().getRows() == 3 && rect.transpose().getCols() == 2);
        eq("取元素 A[1,0]", ma.get(1, 0).toDouble() + "", "3.0");

        System.out.println("-- 矩阵：行列式（精确 + 1..4 阶） --");
        eq("det[[1,2],[3,4]] = −2", ExactMath.INSTANCE.str(ma.det()), "-2");
        eq("det I₃ = 1", ExactMath.INSTANCE.str(Matrix.identity(3).det()), "1");
        eq("det I₄ = 1", ExactMath.INSTANCE.str(Matrix.identity(4).det()), "1");
        eq("det 3×3 = −3", ExactMath.INSTANCE.str(Matrix.ofInts(3, 3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 10})
                .det()), "-3");
        eq("det diag(1,2,3,4) = 24", ExactMath.INSTANCE.str(Matrix.ofInts(4, 4, new int[]{
                1, 0, 0, 0, 0, 2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 4
        }).det()), "24");
        eq("det 分数矩阵 = 1/6",
                ExactMath.INSTANCE.str(Matrix.of(2, 2, vals("1/2", "0", "0", "1/3")).det()), "1/6");
        eq("非方阵 det 报错", errOf(() -> rect.det()), "NumericError");
        eq("5×5 超尺寸报错", errOf(() -> Matrix.identity(5)), "NumericError");
        eq("元素个数不符报错", errOf(() -> Matrix.of(2, 2, vals("1"))), "NumericError");

        System.out.println("-- 矩阵：逆（精确） --");
        eq("det[[1,2],[3,4]] 的逆", ma.inverse().format(), "-2  1\n3/2  -1/2");
        eq("逆矩阵行列式 = 1/det",
                Matrix.ofInts(2, 2, new int[]{2, 0, 0, 3}).inverse().format(), "1/2  0\n0  1/3");
        eq("A × A⁻¹ = I", ma.times(ma.inverse()).format(), "1  0\n0  1");
        eq("I₃ 的逆 = I₃", Matrix.identity(3).inverse().format(),
                "1  0  0\n0  1  0\n0  0  1");
        eq("奇异矩阵求逆报错",
                errOf(() -> Matrix.ofInts(2, 2, new int[]{1, 2, 2, 4}).inverse()), "NumericError");
        eq("非方阵求逆报错", errOf(() -> rect.inverse()), "NumericError");
        eq("分数矩阵求逆",
                Matrix.of(2, 2, vals("1/2", "0", "0", "1/4")).inverse().format(), "2  0\n0  4");

        System.out.println("-- 矩阵：单位阵 / 零阵 / 寄存器 --");
        check("单位阵对角为 1", Matrix.identity(3).get(0, 0).toDouble() == 1.0
                && Matrix.identity(3).get(0, 1).toDouble() == 0.0);
        check("零阵全 0", Matrix.zeros(2, 3).getCells().stream()
                .allMatch(v -> v.toDouble() == 0.0));
        eq("零阵尺寸", Matrix.zeros(2, 3).getRows() + "x" + Matrix.zeros(2, 3).getCols(), "2x3");
        MatrixStore store = new MatrixStore();
        store.set("A", ma);
        check("存入 MatA", store.get("A") != null && store.get("A").getRows() == 2);
        check("未存入返回 null", store.get("B") == null);
        store.set("A", null);
        check("清空单个 MatA", store.get("A") == null);
        store.set("B", ma);
        store.clearAll();
        check("clearAll 后为空", store.snapshot().isEmpty());
        eq("未知矩阵变量报错", errOf(() -> store.set("Q", ma)), "NumericError");
        check("矩阵变量名 A–D", MatrixStore.NAMES.equals(Arrays.asList("A", "B", "C", "D")));

        // ===================================================================
        System.out.println("-- 向量：加减 / 数乘 --");
        Vector3 va = Vector3.ofInts(1, 2, 3);
        Vector3 vb = Vector3.ofInts(4, -5, 6);
        eq("A+B", va.plus(vb).format(), "(5, -3, 9)");
        eq("A−B", va.minus(vb).format(), "(-3, 7, -3)");
        eq("A×2", va.scale(ComplexNum.literal("2")).format(), "(2, 4, 6)");
        eq("分数加法", Vector3.ofLiteral("1/2", "1/3", "0")
                .plus(Vector3.ofLiteral("1/2", "2/3", "1")).format(), "(1, 1, 1)");

        System.out.println("-- 向量：点积 / 叉积 / 模 / 单位化 / 夹角 --");
        eq("A·B = 12", ExactMath.INSTANCE.str(va.dot(vb)), "12");
        eq("分数点积", ExactMath.INSTANCE.str(Vector3.ofLiteral("1/2", "0", "0")
                .dot(Vector3.ofLiteral("2", "0", "0"))), "1");
        eq("A×B = (27,6,−13)", va.cross(vb).format(), "(27, 6, -13)");
        eq("i×j = k", Vector3.ofInts(1, 0, 0).cross(Vector3.ofInts(0, 1, 0)).format(), "(0, 0, 1)");
        eq("A×A = 0", va.cross(va).format(), "(0, 0, 0)");
        near("|(3,0,4)| = 5", Vector3.ofInts(3, 0, 4).norm(), 5.0, 1e-12);
        check("|(3,0,4)| 精确 = 5", Vector3.ofInts(3, 0, 4).normExact().toString().equals("5"));
        check("|(1,1,1)| 无精确值", Vector3.ofInts(1, 1, 1).normExact() == null);
        eq("单位化 (3,0,4)", Vector3.ofInts(3, 0, 4).unit().format(), "(0.6, 0, 0.8)");
        near("单位化后模为 1", Vector3.ofInts(3, 0, 4).unit().norm(), 1.0, 1e-12);
        near("夹角 i⊥j = 90°", Vector3.ofInts(1, 0, 0).angleTo(Vector3.ofInts(0, 1, 0), DEG), 90.0, 1e-9);
        near("夹角 i∥i = 0°", Vector3.ofInts(1, 0, 0).angleTo(Vector3.ofInts(2, 0, 0), DEG), 0.0, 1e-9);
        near("夹角 RAD", Vector3.ofInts(1, 0, 0).angleTo(Vector3.ofInts(1, 1, 0), RAD), Math.PI / 4.0, 1e-12);
        eq("零向量单位化报错", errOf(() -> Vector3.ofInts(0, 0, 0).unit()), "NumericError");
        eq("零向量夹角报错",
                errOf(() -> Vector3.ofInts(0, 0, 0).angleTo(Vector3.ofInts(1, 0, 0), DEG)), "NumericError");
        eq("分数模精确 = 1/2", Vector3.ofLiteral("1/2", "0", "0").normExact().toString(), "1/2");

        System.out.println("-- 向量：寄存器 --");
        VectorStore vs = new VectorStore();
        vs.set("A", va);
        check("存入 VctA", vs.get("A") != null && vs.get("A").getX().toDouble() == 1.0);
        check("未存入返回 null", vs.get("C") == null);
        vs.clearAll();
        check("clearAll 后为空", vs.snapshot().isEmpty());
        eq("未知向量变量报错", errOf(() -> vs.set("Z", va)), "NumericError");
        check("向量变量名 A–D", VectorStore.NAMES.equals(Arrays.asList("A", "B", "C", "D")));

        // ===================================================================
        System.out.println("-- 统计：单变量 --");
        OneVarStat s4 = StatOps.INSTANCE.oneVar(Arrays.asList(1.0, 2.0, 3.0, 4.0));
        check("n = 4", s4.getN() == 4);
        near("Σx = 10", s4.getSum(), 10.0, 1e-12);
        near("Σx² = 30", s4.getSumSq(), 30.0, 1e-12);
        near("均值 = 2.5", s4.getMean(), 2.5, 1e-12);
        near("总体σ = √1.25", s4.getPopSigma(), Math.sqrt(1.25), 1e-12);
        near("样本s = √(5/3)", s4.getSampleS(), Math.sqrt(5.0 / 3.0), 1e-12);
        near("min = 1", s4.getMin(), 1.0, 1e-12);
        near("max = 4", s4.getMax(), 4.0, 1e-12);
        near("中位数（偶数）= 2.5", s4.getMedian(), 2.5, 1e-12);
        OneVarStat s3 = StatOps.INSTANCE.oneVar(Arrays.asList(1.0, 2.0, 3.0));
        near("中位数（奇数）= 2", s3.getMedian(), 2.0, 1e-12);
        near("总体σ([1,2,3]) = √(2/3)", s3.getPopSigma(), Math.sqrt(2.0 / 3.0), 1e-12);
        near("样本s([1,2,3]) = 1", s3.getSampleS(), 1.0, 1e-12);
        OneVarStat s2 = StatOps.INSTANCE.oneVar(Arrays.asList(2.0, 4.0, 6.0));
        near("均值([2,4,6]) = 4", s2.getMean(), 4.0, 1e-12);
        near("总体σ([2,4,6]) = √(8/3)", s2.getPopSigma(), Math.sqrt(8.0 / 3.0), 1e-12);
        eq("空数据报错", errOf(() -> StatOps.INSTANCE.oneVar(new ArrayList<Double>())), "NumericError");
        eq("单点数据样本s 为 NaN",
                "" + StatOps.INSTANCE.oneVar(Arrays.asList(5.0)).getSampleS(), "NaN");

        System.out.println("-- 统计：双变量线性回归 --");
        LinReg lr = StatOps.INSTANCE.linearRegression(
                Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(2.0, 4.0, 6.0));
        near("完全线性 a = 0", lr.getA(), 0.0, 1e-9);
        near("完全线性 b = 2", lr.getB(), 2.0, 1e-9);
        near("完全线性 r = 1", lr.getR(), 1.0, 1e-9);
        near("n = 3", lr.getN(), 3.0, 0.0);
        LinReg lr2 = StatOps.INSTANCE.linearRegression(
                Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0), Arrays.asList(2.0, 4.0, 5.0, 4.0, 5.0));
        near("带噪 a = 2.2", lr2.getA(), 2.2, 1e-9);
        near("带噪 b = 0.6", lr2.getB(), 0.6, 1e-9);
        near("带噪 r = 0.7745967", lr2.getR(), 0.7745966692, 1e-9);
        LinReg lr3 = StatOps.INSTANCE.linearRegression(
                Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(6.0, 4.0, 2.0));
        near("负斜率 b = −2", lr3.getB(), -2.0, 1e-9);
        near("负相关 r = −1", lr3.getR(), -1.0, 1e-9);
        eq("长度不一致报错",
                errOf(() -> StatOps.INSTANCE.linearRegression(Arrays.asList(1.0, 2.0), Arrays.asList(1.0))),
                "NumericError");
        eq("样本不足报错",
                errOf(() -> StatOps.INSTANCE.linearRegression(Arrays.asList(1.0), Arrays.asList(1.0))),
                "NumericError");
        eq("x 全相同报错",
                errOf(() -> StatOps.INSTANCE.linearRegression(
                        Arrays.asList(1.0, 1.0, 1.0), Arrays.asList(1.0, 2.0, 3.0))), "NumericError");

        // ===================================================================
        System.out.println("-- 分布：正态 P / Q / R --");
        near("Φ(0) = 0.5", DistrOps.INSTANCE.normCdf(0.0), 0.5, 1e-6);
        near("Φ(1.96) ≈ 0.975", DistrOps.INSTANCE.normCdf(1.96), 0.9750021049, 1e-6);
        near("φ(0) = 1/√(2π)", DistrOps.INSTANCE.normPdf(0.0), 1.0 / Math.sqrt(2 * Math.PI), 1e-12);
        near("erf(1) ≈ 0.8427008", DistrOps.INSTANCE.erf(1.0), 0.8427007929, 1e-6);
        near("erf(−1) = −erf(1)", DistrOps.INSTANCE.erf(-1.0), -0.8427007929, 1e-6);
        check("P+Q = 1", Math.abs(DistrOps.INSTANCE.normal(3.0, 2.0, 1.5).getP()
                + DistrOps.INSTANCE.normal(3.0, 2.0, 1.5).getQ() - 1.0) < 1e-12);
        near("N(0,1) at x=0 → P = 0.5", DistrOps.INSTANCE.normal(0.0, 0.0, 1.0).getP(), 0.5, 1e-6);
        near("N(0,1) at x=0 → R = 0", DistrOps.INSTANCE.normal(0.0, 0.0, 1.0).getR(), 0.0, 1e-12);
        near("N(0,1) at x=1 → P = Φ(1)", DistrOps.INSTANCE.normal(1.0, 0.0, 1.0).getP(),
                0.8413447461, 1e-6);
        near("N(0,1) at x=1 → R = 2Φ(1)−1", DistrOps.INSTANCE.normal(1.0, 0.0, 1.0).getR(),
                0.6826894921, 1e-6);
        near("N(μ=0,σ=2) at x=1 → P = Φ(0.5)", DistrOps.INSTANCE.normal(1.0, 0.0, 2.0).getP(),
                0.6914624613, 1e-6);
        near("N(μ=10,σ=2) at x=12 → t=1", DistrOps.INSTANCE.normal(12.0, 10.0, 2.0).getP(),
                0.8413447461, 1e-6);
        eq("σ ≤ 0 报错", errOf(() -> DistrOps.INSTANCE.normal(0.0, 0.0, 0.0)), "NumericError");
        eq("σ 为负报错", errOf(() -> DistrOps.INSTANCE.normal(0.0, 0.0, -1.0)), "NumericError");

        System.out.println("-- 分布：二项分布 --");
        near("B(10,0.5) P(X=5) = 0.24609375",
                DistrOps.INSTANCE.binomialPdf(10, 5, 0.5), 0.24609375, 1e-12);
        near("B(10,0.5) P(X≤5) = 0.623046875",
                DistrOps.INSTANCE.binomialCdf(10, 5, 0.5), 0.623046875, 1e-12);
        near("B(4,0.5) P(X=2) = 6/16", DistrOps.INSTANCE.binomialPdf(4, 2, 0.5), 0.375, 1e-12);
        near("B(0,0.3) P(X=0) = 1", DistrOps.INSTANCE.binomialPdf(0, 0, 0.3), 1.0, 1e-12);
        near("B(5,0) P(X=0) = 1", DistrOps.INSTANCE.binomialPdf(5, 0, 0.0), 1.0, 1e-12);
        near("B(5,1) P(X=5) = 1", DistrOps.INSTANCE.binomialPdf(5, 5, 1.0), 1.0, 1e-12);
        near("B(5,0.4) P(X=7) = 0（越界）", DistrOps.INSTANCE.binomialPdf(5, 7, 0.4), 0.0, 0.0);
        near("B(10,0.5) P(X≤10) = 1", DistrOps.INSTANCE.binomialCdf(10, 10, 0.5), 1.0, 1e-12);
        eq("p > 1 报错", errOf(() -> DistrOps.INSTANCE.binomialPdf(5, 2, 1.5)), "NumericError");
        eq("p < 0 报错", errOf(() -> DistrOps.INSTANCE.binomialPdf(5, 2, -0.1)), "NumericError");

        System.out.println("-- 分布：泊松分布 --");
        near("Poisson(2) P(X=0) = e⁻²",
                DistrOps.INSTANCE.poissonPdf(2.0, 0), Math.exp(-2.0), 1e-12);
        near("Poisson(2) P(X=2) = 2e⁻²",
                DistrOps.INSTANCE.poissonPdf(2.0, 2), 2.0 * Math.exp(-2.0), 1e-12);
        near("Poisson(2) P(X≤2) = 5e⁻²",
                DistrOps.INSTANCE.poissonCdf(2.0, 2), 5.0 * Math.exp(-2.0), 1e-12);
        near("Poisson(3) P(X=3) = 4.5e⁻³",
                DistrOps.INSTANCE.poissonPdf(3.0, 3), 4.5 * Math.exp(-3.0), 1e-12);
        near("P(X≤−1) = 0", DistrOps.INSTANCE.poissonCdf(2.0, -1), 0.0, 0.0);
        eq("λ ≤ 0 报错", errOf(() -> DistrOps.INSTANCE.poissonPdf(0.0, 1)), "NumericError");
        near("lnΓ(5) = ln(24)", DistrOps.INSTANCE.lnGamma(5.0), Math.log(24.0), 1e-9);
        near("lnΓ(1) = 0", DistrOps.INSTANCE.lnGamma(1.0), 0.0, 1e-9);

        // ===================================================================
        System.out.println("-- 函数帮助：目录完整性 / 语法键 --");
        check("条目数 ≥ 30", FuncHelp.COUNT >= 30);
        check("ALL 长度 == COUNT", FuncHelp.ALL.size() == FuncHelp.COUNT);
        Set<String> names = new HashSet<>();
        boolean unique = true;
        boolean fieldsOk = true;
        boolean parenOk = true;
        int syntaxErrors = 0;
        for (FuncHelpEntry e : FuncHelp.ALL) {
            if (!names.add(e.getName())) unique = false;
            if (e.getName().isEmpty() || e.getSyntax().isEmpty() || e.getDesc().isEmpty()
                    || e.getInsert().isEmpty()) fieldsOk = false;
            long open = e.getSyntax().chars().filter(c -> c == '(').count();
            long close = e.getSyntax().chars().filter(c -> c == ')').count();
            if (open != close) parenOk = false;
            try {
                CalcEngine.INSTANCE.evaluateValue(e.getSample(), RAD, 0.0, 0.0, null, 0.0,
                        new java.util.HashMap<String, Double>());
            } catch (Throwable t) {
                // 数学错误（定义域）可接受，语法必须对
                if (t instanceof CalcSyntaxError) {
                    syntaxErrors++;
                    System.out.println("    语法错误样例: " + e.getName() + " -> " + e.getSample());
                }
            }
        }
        check("函数短名唯一", unique);
        check("每条都有 name / syntax / desc / insert", fieldsOk);
        check("语法括号配对", parenOk);
        check("所有样例都能通过语法解析（0 语法错误）", syntaxErrors == 0);
        check("按类别分组非空", !FuncHelp.byCategory().isEmpty());
        int catTotal = 0;
        for (Object o : FuncHelp.byCategory()) catTotal += ((List<?>) ((kotlin.Pair<?, ?>) o).getSecond()).size();
        check("分类分组覆盖全部条目", catTotal == FuncHelp.COUNT);

        List<String> inserts = new ArrayList<>();
        for (FuncHelpEntry e : FuncHelp.ALL) inserts.add(e.getInsert());
        String[] mustHave = {
                "sin(", "cos(", "tan(", "sin\u207B\u00B9(", "cos\u207B\u00B9(", "tan\u207B\u00B9(",
                "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
                "log(", "ln(", "logb(", "exp(", "10^", "\u221A(", "cbrt(", "root(",
                "abs(", "npr(", "ncr(", "!", "%", "\u03C0", "e", "Ans", "PreAns", "M",
                "A", "\u2220",
        };
        boolean coverOk = true;
        StringBuilder missing = new StringBuilder();
        for (String m : mustHave) {
            if (!inserts.contains(m)) {
                coverOk = false;
                missing.append(m).append(' ');
            }
        }
        check("语法键完整覆盖引擎函数（缺失：" + (missing.length() == 0 ? "无" : missing.toString().trim()) + "）", coverOk);
        check("类别常量齐全",
                FuncHelp.CAT_TRIG.equals("三角函数") && FuncHelp.CAT_LOG.equals("对数与指数")
                        && FuncHelp.CAT_COMB.equals("排列组合"));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
