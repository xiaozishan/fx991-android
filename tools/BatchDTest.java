import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.BaseN;
import io.paimon.fx991.engine.EqnMode;
import io.paimon.fx991.engine.RatioOps;
import io.paimon.fx991.engine.Rational;
import io.paimon.fx991.engine.SolveResult;
import io.paimon.fx991.engine.TableGen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 批次 D 回归：方程模式 EQN（多项式 2/3/4 次 + 联立线性 2~4 元）/
 * 基数换算 BASE-N / 函数表 TABLE / 比例 RATIO。
 * 全部走纯 Kotlin 的 engine 层（可 JVM 直跑）。
 */
public class BatchDTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;

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

    // ---- 便捷构造 ----

    static Rational rc(String s) {
        return EqnMode.INSTANCE.parseCoeff(s, "t");
    }

    static List<Rational> coeffs(String... ss) {
        List<Rational> out = new ArrayList<>();
        for (String s : ss) out.add(rc(s));
        return out;
    }

    static List<List<Rational>> mat(String... rows) {
        List<List<Rational>> out = new ArrayList<>();
        for (String r : rows) {
            List<Rational> row = new ArrayList<>();
            for (String s : r.split(" ")) row.add(rc(s));
            out.add(row);
        }
        return out;
    }

    static String polyText(String... ss) {
        return EqnMode.INSTANCE.solvePolynomial(coeffs(ss)).getText();
    }

    static String linText(List<List<Rational>> a, String... b) {
        return EqnMode.INSTANCE.solveLinear(a, coeffs(b)).getText();
    }

    static String fmt(long raw, int base, int bits) {
        return BaseN.INSTANCE.format(raw, base, bits);
    }

    static long parse(String s, int base, int bits) {
        return BaseN.INSTANCE.parse(s, base, bits);
    }

    public static void main(String[] args) {

        // ===================================================================
        System.out.println("-- EQN：多项式（精确根）--");
        eq("polyString 1,-3,2", EqnMode.INSTANCE.polyString(coeffs("1", "-3", "2")), "1x^2-3x+2");
        eq("x²-3x+2=0", polyText("1", "-3", "2"), "x = 1, 2");
        eq("x²+1=0（复根）", polyText("1", "0", "1"), "x = ±i");
        eq("x²-2=0（根式）", polyText("1", "0", "-2"), "x = ±√2");
        eq("x²+2x+2=0", polyText("1", "2", "2"), "x = -1 ± i");
        eq("x³-6x²+11x-6=0", polyText("1", "-6", "11", "-6"), "x = 1, 2, 3");
        eq("x³=0（重根去重）", polyText("1", "0", "0", "0"), "x = 0");
        eq("x⁴-5x²+4=0", polyText("1", "0", "-5", "0", "4"), "x = -2, -1, 1, 2");
        eq("小系数精确往返 0.5x²-1.5x+1=0", polyText("0.5", "-1.5", "1"), "x = 1, 2");
        eq("最高次系数为 0 报错",
                errOf(() -> EqnMode.INSTANCE.solvePolynomial(coeffs("0", "1", "2"))),
                "NumericError");

        System.out.println("-- EQN：多项式数值兜底（Durand-Kerner，含复根）--");
        SolveResult dk = EqnMode.INSTANCE.solvePolynomial(coeffs("1", "0", "-2", "-5"));
        eq("x³-2x-5 类型", dk.getKind().toString(), "SOLUTIONS");
        check("x³-2x-5 含实根 2.09455148", dk.getText().contains("2.09455148"));
        check("x³-2x-5 含复根（±i）", dk.getText().contains("±") && dk.getText().contains("i"));
        check("x³-2x-5 标注数值解", dk.getNote().contains("数值解"));
        SolveResult q4 = EqnMode.INSTANCE.solvePolynomial(coeffs("1", "0", "0", "0", "1"));
        eq("x⁴+1 类型", q4.getKind().toString(), "SOLUTIONS");
        check("x⁴+1 全是复根", q4.getText().contains("i") && q4.getText().contains("±"));

        double[] flat = EqnMode.INSTANCE.durandKerner(new double[]{1, 0, -2, -5});
        boolean foundReal = false;
        boolean foundConj = false;
        for (int i = 0; i < flat.length / 2; i++) {
            double re = flat[2 * i], im = Math.abs(flat[2 * i + 1]);
            if (im < 1e-9 && Math.abs(re - 2.0945514815423265) < 1e-8) foundReal = true;
            if (Math.abs(re + 1.0472757407711633) < 1e-8 && Math.abs(im - 1.1359398890929285) < 1e-8)
                foundConj = true;
        }
        check("DK 实根 ≈ 2.0945514815", foundReal);
        check("DK 复根 ≈ -1.0472757 ± 1.1359399i", foundConj);
        double[] flat4 = EqnMode.INSTANCE.durandKerner(new double[]{1, 0, 0, 0, 1});
        boolean allUnit = true;
        double halfSqrt2 = Math.sqrt(2.0) / 2.0;
        for (int i = 0; i < 4; i++) {
            double re = flat4[2 * i], im = flat4[2 * i + 1];
            if (Math.abs(Math.abs(re) - halfSqrt2) > 1e-8) allUnit = false;
            if (Math.abs(Math.abs(im) - halfSqrt2) > 1e-8) allUnit = false;
        }
        check("DK x⁴+1 四根 = ±√2/2 ± √2/2 i", allUnit);

        System.out.println("-- EQN：联立线性方程组（高斯消元 + 精确分数）--");
        eq("2 元：2x+y=5, x-y=1", linText(mat("2 1", "1 -1"), "5", "1"), "x = 2, y = 1");
        eq("2 元分数解", linText(mat("2 4", "6 -2"), "10", "4"), "x = 9/7, y = 13/7");
        eq("3 元", linText(mat("1 1 1", "2 -1 1", "3 2 -1"), "6", "3", "4"),
                "x = 1, y = 2, z = 3");
        eq("4 元", linText(mat("1 1 1 1", "2 1 -1 1", "1 -1 1 -1", "1 2 3 4"),
                "10", "5", "-2", "30"), "x = 1, y = 2, z = 3, w = 4");
        SolveResult noSol = EqnMode.INSTANCE.solveLinear(mat("1 1", "1 1"), coeffs("1", "2"));
        eq("不相容 → 无解", noSol.getText(), "无解");
        eq("无解类型 NONE", noSol.getKind().toString(), "NONE");
        SolveResult inf = EqnMode.INSTANCE.solveLinear(mat("1 1", "2 2"), coeffs("3", "6"));
        eq("相关方程 → 无穷多解", inf.getText(), "无穷多解");
        eq("无穷多解类型 INFINITE", inf.getKind().toString(), "INFINITE");
        eq("系数矩阵形状错误",
                errOf(() -> EqnMode.INSTANCE.solveLinear(mat("1 1 1", "2 2"), coeffs("3", "6"))),
                "NumericError");

        // ===================================================================
        System.out.println("-- BASE-N：解析与四进制显示 --");
        eq("DEC 255 → HEX", fmt(parse("255", 10, 16), 16, 16), "FF");
        eq("DEC 255 → BIN", fmt(parse("255", 10, 16), 2, 16), "11111111");
        eq("DEC 255 → OCT", fmt(parse("255", 10, 16), 8, 16), "377");
        eq("HEX FF → DEC", fmt(parse("FF", 16, 16), 10, 16), "255");
        eq("HEX 小写 ff → DEC", fmt(parse("ff", 16, 16), 10, 16), "255");
        eq("BIN 1010 → DEC", fmt(parse("1010", 2, 16), 10, 16), "10");
        eq("OCT 377 → DEC", fmt(parse("377", 8, 16), 10, 16), "255");

        System.out.println("-- BASE-N：负数补码 --");
        long m1 = parse("-1", 10, 16);
        eq("-1（16 位）DEC", fmt(m1, 10, 16), "-1");
        eq("-1（16 位）HEX", fmt(m1, 16, 16), "FFFF");
        eq("-1（16 位）BIN", fmt(m1, 2, 16), "1111111111111111");
        eq("-1（16 位）OCT", fmt(m1, 8, 16), "177777");
        eq("-1（16 位）无符号", BaseN.INSTANCE.unsignedDec(m1, 16), "65535");
        eq("HEX FFFF → DEC -1", fmt(parse("FFFF", 16, 16), 10, 16), "-1");
        eq("HEX 8000 → DEC -32768", fmt(parse("8000", 16, 16), 10, 16), "-32768");
        eq("DEC 4294967295（32 位）→ -1", fmt(parse("4294967295", 10, 32), 10, 32), "-1");
        eq("64 位 FFFFFFFFFFFFFFFF → -1",
                fmt(parse("FFFFFFFFFFFFFFFF", 16, 64), 10, 64), "-1");
        eq("64 位最小值 DEC 往返",
                fmt(parse("-9223372036854775808", 10, 64), 10, 64), "-9223372036854775808");
        eq("64 位最小值 HEX", fmt(parse("-9223372036854775808", 10, 64), 16, 64),
                "8000000000000000");
        eq("字长切换：-1 在 32 位 HEX", fmt(parse("-1", 10, 32), 16, 32), "FFFFFFFF");
        eq("字长切换：-1 在 64 位 HEX", fmt(parse("-1", 10, 64), 16, 64), "FFFFFFFFFFFFFFFF");

        System.out.println("-- BASE-N：输入校验 --");
        eq("BIN 含 2 报错", errOf(() -> parse("12", 2, 16)), "NumericError");
        eq("OCT 含 8 报错", errOf(() -> parse("8", 8, 16)), "NumericError");
        eq("HEX 含 G 报错", errOf(() -> parse("G", 16, 16)), "NumericError");
        eq("空串报错", errOf(() -> parse("", 10, 16)), "NumericError");
        eq("超字长 DEC 65536@16 报错", errOf(() -> parse("65536", 10, 16)), "NumericError");
        eq("超字长 HEX 10000@16 报错", errOf(() -> parse("10000", 16, 16)), "NumericError");

        System.out.println("-- BASE-N：位运算 --");
        eq("F0 AND 3C = 48", fmt(BaseN.INSTANCE.bitAnd(parse("F0", 16, 16), parse("3C", 16, 16), 16), 10, 16), "48");
        eq("F0 OR 0F = FF", fmt(BaseN.INSTANCE.bitOr(parse("F0", 16, 16), parse("0F", 16, 16), 16), 16, 16), "FF");
        eq("FF XOR 0F = F0", fmt(BaseN.INSTANCE.bitXor(parse("FF", 16, 16), parse("0F", 16, 16), 16), 16, 16), "F0");
        eq("FF XNOR 0F = FF0F", fmt(BaseN.INSTANCE.bitXnor(parse("FF", 16, 16), parse("0F", 16, 16), 16), 16, 16), "FF0F");
        eq("FF XNOR 0F → DEC -241", fmt(BaseN.INSTANCE.bitXnor(parse("FF", 16, 16), parse("0F", 16, 16), 16), 10, 16), "-241");
        eq("NOT 0（16 位）= -1", fmt(BaseN.INSTANCE.bitNot(0, 16), 10, 16), "-1");
        eq("NOT 0（16 位）HEX", fmt(BaseN.INSTANCE.bitNot(0, 16), 16, 16), "FFFF");
        eq("NEG 1 = -1", fmt(BaseN.INSTANCE.neg(1, 16), 10, 16), "-1");
        eq("NEG 5（32 位）HEX", fmt(BaseN.INSTANCE.neg(5, 32), 16, 32), "FFFFFFFB");

        // ===================================================================
        System.out.println("-- TABLE：函数表 --");
        TableGen.TableData t1 = TableGen.INSTANCE.generate("x^2", "", 1, 5, 1, DEG);
        eq("x² 1..5 步长 1 → 5 行", String.valueOf(t1.getRows().size()), "5");
        near("第 1 行 f(1)=1", t1.getRows().get(0).getF(), 1.0, 1e-12);
        near("第 5 行 f(5)=25", t1.getRows().get(4).getF(), 25.0, 1e-12);
        check("无 g 时 hasG=false", !t1.getHasG());
        TableGen.TableData t2 = TableGen.INSTANCE.generate("x^2", "2x", 1, 3, 1, DEG);
        check("双函数 hasG=true", t2.getHasG());
        near("g(2)=4", t2.getRows().get(1).getG(), 4.0, 1e-12);
        TableGen.TableData t3 = TableGen.INSTANCE.generate("x", "", 1, 2, 0.5, DEG);
        eq("步长 0.5 → 3 行", String.valueOf(t3.getRows().size()), "3");
        near("中间行 x=1.5", t3.getRows().get(1).getX(), 1.5, 1e-12);
        eq("步长 0 报错", errOf(() -> TableGen.INSTANCE.generate("x", "", 1, 5, 0, DEG)),
                "NumericError");
        eq("方向不一致报错", errOf(() -> TableGen.INSTANCE.generate("x", "", 1, 5, -1, DEG)),
                "NumericError");
        eq("行数超限报错", errOf(() -> TableGen.INSTANCE.generate("x", "", 0, 1000, 1, DEG)),
                "NumericError");
        eq("f 语法错误报错", errOf(() -> TableGen.INSTANCE.generate("1+(", "", 1, 2, 1, DEG)),
                "NumericError");
        TableGen.TableData t4 = TableGen.INSTANCE.generate("1/x", "", 0, 2, 1, DEG);
        check("1/x 在 x=0 该格为 null", t4.getRows().get(0).getF() == null);
        near("1/x 在 x=1 正常", t4.getRows().get(1).getF(), 1.0, 1e-12);
        TableGen.TableData t5 = TableGen.INSTANCE.generate("sin(x)", "", 30, 30, 1, DEG);
        near("sin(30°)=0.5（角度制跟随）", t5.getRows().get(0).getF(), 0.5, 1e-12);

        // ===================================================================
        System.out.println("-- RATIO：比例 --");
        eq("a:b=c:x → 2:3=4:x → x=6",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.CX, rc("2"), rc("3"), rc("4"))), "6");
        eq("a:b=c:x 分数解 4:2=3:x → 3/2",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.CX, rc("4"), rc("2"), rc("3"))), "3/2（≈ 1.5）");
        eq("a:b=x:d → 2:3=x:9 → x=6",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.XD, rc("2"), rc("3"), rc("9"))), "6");
        eq("a:b=x:d 分数解 3:2=x:1 → 3/2",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.XD, rc("3"), rc("2"), rc("1"))), "3/2（≈ 1.5）");
        eq("小数输入 0.5:1.5=2:x → 6",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.CX, rc("0.5"), rc("1.5"), rc("2"))), "6");
        eq("负数 -1:2=3:x → -6",
                RatioOps.INSTANCE.textOf(RatioOps.INSTANCE.solve(
                        RatioOps.Form.CX, rc("-1"), rc("2"), rc("3"))), "-6");
        eq("CX 中 a=0 报错",
                errOf(() -> RatioOps.INSTANCE.solve(RatioOps.Form.CX, rc("0"), rc("1"), rc("1"))),
                "NumericError");
        eq("XD 中 b=0 报错",
                errOf(() -> RatioOps.INSTANCE.solve(RatioOps.Form.XD, rc("1"), rc("0"), rc("1"))),
                "NumericError");
        eq("非法系数报错",
                errOf(() -> RatioOps.INSTANCE.parseCoefficient("abc", "a")), "NumericError");

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
