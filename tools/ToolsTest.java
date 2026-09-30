import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.ComplexRect;
import io.paimon.fx991.engine.NumberNotation;
import io.paimon.fx991.engine.PolarForm;
import io.paimon.fx991.engine.PolarPair;
import io.paimon.fx991.engine.RandomOps;
import io.paimon.fx991.engine.Registers;
import io.paimon.fx991.engine.SciConstant;
import io.paimon.fx991.engine.SciConstants;
import io.paimon.fx991.engine.SiPrefixes;
import io.paimon.fx991.engine.UnitCategory;
import io.paimon.fx991.engine.UnitConvert;
import io.paimon.fx991.engine.Value;
import io.paimon.fx991.ui.NatModelKt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次 B 回归：STO 变量 / PreAns / Exp / 绝对值 / ENG·SCI 显示 /
 * 科学常数 / 单位换算（含温度）/ SI 前缀 / 随机数 / Pol / Rec。
 */
public class ToolsTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;
    static final AngleMode GRAD = AngleMode.GRAD;

    static final String MUL = "\u00D7";
    static final String DIV = "\u00F7";
    static final String SUP1 = "\u00B9";
    static final String SUP3 = "\u00B3";
    static final String SUP4 = "\u2074";
    static final String SUP6 = "\u2076";
    static final String SUP9 = "\u2079";
    static final String SUPM = "\u207B";

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
    }

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " got=" + got + "  expect=" + expect);
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

    static Map<String, Double> vars(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Double) kv[i + 1]);
        return m;
    }

    /** 双轨求值 → 默认显示 */
    static String fmt(String expr) {
        return fmt(expr, RAD, 0.0, null, 0.0, new HashMap<String, Double>());
    }

    static String fmt(String expr, AngleMode m, double preAns,
                      Double ansVal, double mem, Map<String, Double> v) {
        try {
            Value ans = ansVal == null ? null : new Value.Floating(ansVal.doubleValue());
            return CalcEngine.INSTANCE.formatValue(
                    CalcEngine.INSTANCE.evaluateValue(expr, m, 0.0, mem, ans, preAns, v),
                    false, false, 10, null, NumberNotation.NORM);
        } catch (Throwable e) {
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    static String errOf(Runnable r) {
        try {
            r.run();
            return "none";
        } catch (Throwable e) {
            return e.getClass().getSimpleName();
        }
    }

    static String pow10(int n) {
        StringBuilder b = new StringBuilder("1");
        for (int i = 0; i < n; i++) b.append('0');
        return b.toString();
    }

    public static void main(String[] args) {

        System.out.println("-- 科学常数表（≥ 20 条 + 取值抽查） --");
        check("常数条数 ≥ 20", SciConstants.COUNT >= 20);
        check("表长度 == COUNT", SciConstants.ALL.size() == SciConstants.COUNT);
        near("光速 c", findConst("c").getValue(), 299792458.0, 0.0);
        near("普朗克 h", findConst("h").getValue(), 6.62607015e-34, 0.0);
        near("元电荷 e", findConst("e").getValue(), 1.602176634e-19, 0.0);
        near("阿伏伽德罗 NA", findConst("NA").getValue(), 6.02214076e23, 0.0);
        near("玻尔兹曼 k", findConst("k").getValue(), 1.380649e-23, 0.0);
        near("引力常数 G", findConst("G").getValue(), 6.67430e-11, 0.0);
        near("电子质量 me", findConst("me").getValue(), 9.1093837015e-31, 0.0);
        near("标准重力 g", findConst("g").getValue(), 9.80665, 0.0);
        check("常数带单位", findConst("c").getUnit().equals("m/s"));
        check("常数带名称", findConst("c").getName().length() > 0);

        System.out.println("-- 常数插入表达式用的字面量 literal() --");
        eq("literal(299792458)", CalcEngine.INSTANCE.literal(299792458.0), "299792458");
        eq("literal(6.62607015e-34)", CalcEngine.INSTANCE.literal(6.62607015e-34),
                "6.62607015" + MUL + "10^-34");
        eq("literal(1e-34)", CalcEngine.INSTANCE.literal(1e-34), "1" + MUL + "10^-34");
        eq("literal(0)", CalcEngine.INSTANCE.literal(0.0), "0");
        eq("插入光速可直接求值", fmt(CalcEngine.INSTANCE.literal(299792458.0)), "299792458");
        eq("插入 1e-34（精确轨）= 1/10³⁴", fmt(CalcEngine.INSTANCE.literal(1e-34)), "1/" + pow10(34));
        near("插入普朗克数值正确",
                CalcEngine.INSTANCE.evaluate(
                        "2" + MUL + "(" + CalcEngine.INSTANCE.literal(6.62607015e-34) + ")", RAD, 0.0, 0.0),
                2.0 * 6.62607015e-34, 1e-48);

        System.out.println("-- 单位换算：长度 / 质量 / 时间 / 面积 / 体积 --");
        near("1 km → m", uconv(UnitConvert.LENGTH, "km", "m", 1.0), 1000.0, 1e-9);
        near("1 m → cm", uconv(UnitConvert.LENGTH, "m", "cm", 1.0), 100.0, 1e-9);
        near("1 in → cm", uconv(UnitConvert.LENGTH, "in", "cm", 1.0), 2.54, 1e-9);
        near("1 mi → km", uconv(UnitConvert.LENGTH, "mi", "km", 1.0), 1.609344, 1e-9);
        near("1 nmi → m", uconv(UnitConvert.LENGTH, "nmi", "m", 1.0), 1852.0, 1e-9);
        near("1 kg → g", uconv(UnitConvert.MASS, "kg", "g", 1.0), 1000.0, 1e-9);
        near("1 lb → g", uconv(UnitConvert.MASS, "lb", "g", 1.0), 453.59237, 1e-9);
        near("1 t → kg", uconv(UnitConvert.MASS, "t", "kg", 1.0), 1000.0, 1e-9);
        near("1 h → min", uconv(UnitConvert.TIME, "h", "min", 1.0), 60.0, 1e-9);
        near("1 d → h", uconv(UnitConvert.TIME, "d", "h", 1.0), 24.0, 1e-9);
        near("1 ha → m²", uconv(UnitConvert.AREA, "ha", "m²", 1.0), 10000.0, 1e-9);
        near("1 亩 → m²", uconv(UnitConvert.AREA, "亩", "m²", 1.0), 666.6666667, 1e-6);
        near("1 acre → m²", uconv(UnitConvert.AREA, "acre", "m²", 1.0), 4046.8564224, 1e-6);
        near("1 m³ → L", uconv(UnitConvert.VOLUME, "m³", "L", 1.0), 1000.0, 1e-9);
        near("1 L → mL", uconv(UnitConvert.VOLUME, "L", "mL", 1.0), 1000.0, 1e-9);
        near("1 gal → L", uconv(UnitConvert.VOLUME, "gal", "L", 1.0), 3.785411784, 1e-9);
        check("类别数 == 6", UnitConvert.CATEGORIES.size() == 6);

        System.out.println("-- 单位换算：温度（非倍数关系，单独处理） --");
        check("温度类别标记为 affine", UnitConvert.TEMPERATURE.getAffine());
        near("100 °C → °F", uconv(UnitConvert.TEMPERATURE, "°C", "°F", 100.0), 212.0, 1e-9);
        near("32 °F → °C", uconv(UnitConvert.TEMPERATURE, "°F", "°C", 32.0), 0.0, 1e-9);
        near("-40 °C → °F", uconv(UnitConvert.TEMPERATURE, "°C", "°F", -40.0), -40.0, 1e-9);
        near("0 °C → K", uconv(UnitConvert.TEMPERATURE, "°C", "K", 0.0), 273.15, 1e-9);
        near("300 K → °C", uconv(UnitConvert.TEMPERATURE, "K", "°C", 300.0), 26.85, 1e-9);
        near("212 °F → K", uconv(UnitConvert.TEMPERATURE, "°F", "K", 212.0), 373.15, 1e-9);
        near("0 K → °C", uconv(UnitConvert.TEMPERATURE, "K", "°C", 0.0), -273.15, 1e-9);

        System.out.println("-- SI 前缀换算 --");
        near("1 k → 无", SiPrefixes.INSTANCE.convert(idxP("k"), SiPrefixes.NONE_INDEX, 1.0), 1000.0, 1e-9);
        near("1 无 → m", SiPrefixes.INSTANCE.convert(SiPrefixes.NONE_INDEX, idxP("m"), 1.0), 1000.0, 1e-9);
        near("1 m → μ", SiPrefixes.INSTANCE.convert(idxP("m"), idxP("μ"), 1.0), 1000.0, 1e-9);
        near("1 M → k", SiPrefixes.INSTANCE.convert(idxP("M"), idxP("k"), 1.0), 1000.0, 1e-9);
        near("1 G → M", SiPrefixes.INSTANCE.convert(idxP("G"), idxP("M"), 1.0), 1000.0, 1e-9);
        near("1 n → p", SiPrefixes.INSTANCE.convert(idxP("n"), idxP("p"), 1.0), 1000.0, 1e-9);
        near("1 c → m", SiPrefixes.INSTANCE.convert(idxP("c"), idxP("m"), 1.0), 10.0, 1e-9);
        near("2.5 k → 无", SiPrefixes.INSTANCE.convert(idxP("k"), SiPrefixes.NONE_INDEX, 2.5), 2500.0, 1e-9);
        check("SI 覆盖 p/n/μ/m/c/k/M/G",
                idxP("p") >= 0 && idxP("n") >= 0 && idxP("μ") >= 0 && idxP("m") >= 0
                        && idxP("c") >= 0 && idxP("k") >= 0 && idxP("M") >= 0 && idxP("G") >= 0);

        System.out.println("-- 随机数 Ran# / RanInt --");
        boolean uniformOk = true;
        double uMin = 1.0, uMax = 0.0;
        for (int i = 0; i < 2000; i++) {
            double u = RandomOps.INSTANCE.uniform();
            if (u < 0.0 || u >= 1.0) uniformOk = false;
            uMin = Math.min(uMin, u);
            uMax = Math.max(uMax, u);
        }
        check("Ran# 2000 次都在 [0,1)", uniformOk);
        check("Ran# 有散布（非恒定值）", uMax - uMin > 0.5);
        boolean intOk = true;
        for (int i = 0; i < 2000; i++) {
            long val = RandomOps.INSTANCE.ranInt(1L, 6L);
            if (val < 1L || val > 6L) intOk = false;
        }
        check("RanInt(1,6) 都在 [1,6]", intOk);
        List<Long> many = RandomOps.INSTANCE.ranInts(10L, 20L, 500);
        check("RanInts 次数正确", many.size() == 500);
        boolean boundsOk = true;
        for (long val : many) if (val < 10L || val > 20L) boundsOk = false;
        check("RanInts 全部落在 [10,20]", boundsOk);
        check("RanInt 单点区间 (7,7) → 7", RandomOps.INSTANCE.ranInt(7L, 7L) == 7L);
        eq("RanInt 上界 < 下界报错", errOf(() -> RandomOps.INSTANCE.ranInt(5L, 1L)), "NumericError");
        eq("RanInts 次数为 0 报错", errOf(() -> RandomOps.INSTANCE.ranInts(1L, 6L, 0)), "NumericError");

        System.out.println("-- 绝对值 |x|（abs） --");
        eq("abs(-3)", fmt("abs(-3)"), "3");
        eq("abs(3)", fmt("abs(3)"), "3");
        eq("abs(-1" + DIV + "4) 保持精确", fmt("abs(-1" + DIV + "4)"), "1/4");
        near("abs(-2.5) 浮点轨", CalcEngine.INSTANCE.evaluate("abs(-2.5)", RAD, 0.0, 0.0), 2.5, 1e-12);
        eq("abs(3-7)", fmt("abs(3-7)"), "4");
        eq("嵌套 abs(abs(-5)-2)", fmt("abs(abs(-5)-2)"), "3");

        System.out.println("-- PreAns 上上次结果 --");
        eq("PreAns+1（PreAns=5）", fmt("PreAns+1", RAD, 5.0, null, 0.0, new HashMap<>()), "6");
        eq("PreAns*2（PreAns=7）", fmt("PreAns*2", RAD, 7.0, null, 0.0, new HashMap<>()), "14");
        eq("PreAns 默认 0", fmt("PreAns+9"), "9");
        eq("Ans 与 PreAns 并存", fmt("Ans+PreAns", RAD, 3.0, 10.0, 0.0, new HashMap<>()), "13");

        System.out.println("-- STO 变量（A–F / x / y） --");
        Map<String, Double> v = vars("A", 2.0, "B", 3.0);
        eq("A+B", fmt("A+B", RAD, 0.0, null, 0.0, v), "5");
        eq("A*B-A", fmt("A*B-A", RAD, 0.0, null, 0.0, v), "4");
        eq("x 变量代入", fmt("x*2", RAD, 0.0, null, 0.0, vars("x", 3.0)), "6");
        eq("y 变量代入", fmt("y+1", RAD, 0.0, null, 0.0, vars("y", 4.0)), "5");
        eq("F 变量（存的是浮点）", fmt("F" + DIV + "2", RAD, 0.0, null, 0.0, vars("F", 5.0)), "2.5");
        eq("未定义变量按 0", fmt("C+1"), "1");
        eq("A^2", fmt("A^2", RAD, 0.0, null, 0.0, vars("A", 12.0)), "144");

        System.out.println("-- STO / CLR ALL 的寄存器层 --");
        Registers reg = new Registers();
        check("初始为空", reg.isEmpty());
        String msg = reg.store("A", 5.0);
        check("存入后有确认提示（含 A 与 5）", msg.contains("A") && msg.contains("5"));
        near("取回 A", reg.get("A"), 5.0, 0.0);
        reg.store("x", 3.0);
        check("快照含 A 与 x", reg.snapshot().containsKey("A") && reg.snapshot().containsKey("x"));
        check("快照条数 = 2", reg.snapshot().size() == 2);
        reg.store("A", 9.0);
        near("覆盖写入 A", reg.get("A"), 9.0, 0.0);
        reg.clearAll();
        check("CLR ALL 后寄存器为空", reg.isEmpty());
        check("CLR ALL 后取回为 null", reg.get("A") == null);
        eq("未知变量报错", errOf(() -> reg.store("Q", 1.0)), "NumericError");
        check("变量键位含 A–F/x/y",
                Registers.VARIABLES.contains("A") && Registers.VARIABLES.contains("F")
                        && Registers.VARIABLES.contains("x") && Registers.VARIABLES.contains("y"));
        check("变量键位不含 M（M 是存储器）", !Registers.VARIABLES.contains("M"));

        System.out.println("-- Exp 科学记数输入（×10^ 结构可求值、可上标显示） --");
        eq("2" + MUL + "10^3", fmt("2" + MUL + "10^3"), "2000");
        eq("1.5" + MUL + "10^-2 精确", fmt("1.5" + MUL + "10^-2"), "3/200");
        eq("2" + MUL + "10^-3 精确", fmt("2" + MUL + "10^-3"), "1/500");
        eq("6" + MUL + "10^8", fmt("6" + MUL + "10^8"), "600000000");
        String[] expLines = NatModelKt.natAsciiText("2" + MUL + "10^3").split("\n");
        check("Exp 自然书写为两行（指数抬升为真上标）", expLines.length == 2);
        StringBuilder expJoin = new StringBuilder();
        for (String l : expLines) expJoin.append(l);
        check("Exp 投影含 2×10", expJoin.toString().contains("2" + MUL + "10"));

        System.out.println("-- ENG 工程记数（指数为 3 的倍数） --");
        eq("12345 → ENG", CalcEngine.INSTANCE.format(12345.0, 10, null, NumberNotation.ENG),
                "12.345" + MUL + "10" + SUP3);
        eq("299792458 → ENG", CalcEngine.INSTANCE.format(299792458.0, 10, null, NumberNotation.ENG),
                "299.792458" + MUL + "10" + SUP6);
        eq("0.0001234 → ENG", CalcEngine.INSTANCE.format(0.0001234, 10, null, NumberNotation.ENG),
                "123.4" + MUL + "10" + SUPM + SUP6);
        eq("2.5 → ENG（指数 0 不写）", CalcEngine.INSTANCE.format(2.5, 10, null, NumberNotation.ENG), "2.5");
        eq("1e9 → ENG", CalcEngine.INSTANCE.format(1e9, 10, null, NumberNotation.ENG),
                "1" + MUL + "10" + SUP9);
        eq("0 → ENG", CalcEngine.INSTANCE.format(0.0, 10, null, NumberNotation.ENG), "0");

        System.out.println("-- SCI 科学记数（与 ENG 协调） --");
        eq("1234 → SCI", CalcEngine.INSTANCE.format(1234.0, 10, null, NumberNotation.SCI),
                "1.234" + MUL + "10" + SUP3);
        eq("0.0001234 → SCI", CalcEngine.INSTANCE.format(0.0001234, 10, null, NumberNotation.SCI),
                "1.234" + MUL + "10" + SUPM + SUP4);
        eq("NORM 仍是普通数", CalcEngine.INSTANCE.format(1234.0, 10, null, NumberNotation.NORM), "1234");
        eq("NORM 仍显示精确分数", fmt("1" + DIV + "3"), "1/3");
        eq("SCI 下精确分数让位小数",
                CalcEngine.INSTANCE.formatValue(
                        CalcEngine.INSTANCE.evaluateValue("1" + DIV + "3", RAD, 0.0, 0.0, null, 0.0, new HashMap<>()),
                        false, false, 10, null, NumberNotation.SCI),
                "3.333333333" + MUL + "10" + SUPM + SUP1);
        check("NumberNotation 循环 NORM→SCI→ENG→NORM",
                NumberNotation.NORM.next() == NumberNotation.SCI
                        && NumberNotation.SCI.next() == NumberNotation.ENG
                        && NumberNotation.ENG.next() == NumberNotation.NORM);

        System.out.println("-- Pol / Rec 坐标换算（角度制跟随设置） --");
        PolarPair p1 = PolarForm.INSTANCE.toPolar(1.0, Math.sqrt(3.0), DEG);
        near("Pol(1,√3) → r", p1.getR(), 2.0, 1e-12);
        near("Pol(1,√3) → θ", p1.getTheta(), 60.0, 1e-9);
        PolarPair p2 = PolarForm.INSTANCE.toPolar(-1.0, 0.0, DEG);
        near("Pol(-1,0) → θ=180°", p2.getTheta(), 180.0, 1e-9);
        PolarPair p3 = PolarForm.INSTANCE.toPolar(0.0, -1.0, DEG);
        near("Pol(0,-1) → θ=-90°", p3.getTheta(), -90.0, 1e-9);
        PolarPair p4 = PolarForm.INSTANCE.toPolar(1.0, 1.0, RAD);
        near("Pol RAD 下 θ=π/4", p4.getTheta(), Math.PI / 4.0, 1e-12);
        PolarPair p5 = PolarForm.INSTANCE.toPolar(1.0, 1.0, GRAD);
        near("Pol GRAD 下 θ=50", p5.getTheta(), 50.0, 1e-9);
        ComplexRect c1 = PolarForm.INSTANCE.toRect(2.0, 60.0, DEG);
        near("Rec(2,60) → x", c1.getRe(), 1.0, 1e-12);
        near("Rec(2,60) → y", c1.getIm(), Math.sqrt(3.0), 1e-12);
        ComplexRect c2 = PolarForm.INSTANCE.toRect(3.0, 90.0, DEG);
        near("Rec(3,90) → x=0", c2.getRe(), 0.0, 1e-12);
        near("Rec(3,90) → y=3", c2.getIm(), 3.0, 1e-12);
        ComplexRect c3 = PolarForm.INSTANCE.toRect(1.0, 100.0, GRAD);
        near("Rec GRAD 100 → y=1", c3.getIm(), 1.0, 1e-12);
        near("Pol→Rec 往返一致", PolarForm.INSTANCE.toRect(p1.getR(), p1.getTheta(), DEG).getRe(), 1.0, 1e-9);

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    static SciConstant findConst(String symbol) {
        for (SciConstant k : SciConstants.ALL) if (k.getSymbol().equals(symbol)) return k;
        throw new AssertionError("no constant " + symbol);
    }

    static double uconv(UnitCategory cat, String from, String to, double value) {
        return UnitConvert.INSTANCE.convert(cat, idx(cat, from), idx(cat, to), value);
    }

    static int idx(UnitCategory cat, String symbol) {
        for (int i = 0; i < cat.getUnits().size(); i++) {
            if (cat.getUnits().get(i).getSymbol().equals(symbol)) return i;
        }
        throw new AssertionError("no unit " + symbol + " in " + cat.getName());
    }

    static int idxP(String symbol) {
        for (int i = 0; i < SiPrefixes.ALL.size(); i++) {
            if (SiPrefixes.ALL.get(i).getSymbol().equals(symbol)) return i;
        }
        return -1;
    }
}
