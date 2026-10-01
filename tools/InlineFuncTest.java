import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.InlineFuncs;
import io.paimon.fx991.engine.Unified;
import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import io.paimon.fx991.ui.NatModelKt;
import io.paimon.fx991.ui.NatCursorKt;
import java.util.HashMap;
import java.util.Map;

/**
 * 批次 K3 回归：主行「就地括号调用」+ ∠ 投影护栏。
 *
 * 覆盖：
 *  1. ∠ 投影不变量 —— 投影（含每个光标位置 / natLinear 往返）里 ∠ 的个数必须等于输入的个数
 *  2. 新模板的投影：int(式,下,上) → ∫ 二维模板（含空槽光标落点）；deriv → d/dx；sum → Σ
 *  3. 插入后光标落点（CursorModel 模拟 ViewModel 的 insert+back 语义）
 *  4. 主行求值：int / deriv / sum / lim / calc / dms / pol / rec / ranint / const / si / conv 各一组
 *  5. solve / sto 外壳拆解（InlineFuncs 纯函数）
 */
public class InlineFuncTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;
    static final String ANGL = "∠";

    static void check(String what, boolean good) {
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ") + what);
    }

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 40) + " got=" + got + "  expect=" + expect);
    }

    static void eq(String what, Object got, Object expect) {
        boolean good = expect == null ? got == null : expect.equals(got);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 40) + " => " + got + (good ? "" : " (expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    /** 主行求值取实部（复数轨要求虚部≈0） */
    static double re(String expr, AngleMode m) {
        return re(expr, m, new HashMap<String, Double>());
    }

    static double re(String expr, AngleMode m, Map<String, Double> vars) {
        CalcValue v = Unified.evaluate(expr, m, 0.0, 0.0, null, 0.0, vars, null, null);
        ComplexNum c = Unified.asComplex(v);
        if (c == null) throw new IllegalStateException("非标量结果: " + expr);
        if (Math.abs(c.getIm().toDouble()) > 1e-9) throw new IllegalStateException("结果带虚部: " + expr);
        return c.getRe().toDouble();
    }

    static ComplexNum cx(String expr, AngleMode m) {
        CalcValue v = Unified.evaluate(expr, m, 0.0, 0.0, null, 0.0,
                new HashMap<String, Double>(), null, null);
        ComplexNum c = Unified.asComplex(v);
        if (c == null) throw new IllegalStateException("非标量结果: " + expr);
        return c;
    }

    /** ∠ 投影不变量：纯投影 + 每个光标位置 + natLinear 往返，∠ 个数都必须等于输入 */
    static void angleInvariant(String expr) {
        int in = count(expr, ANGL);
        check("投影∠个数: " + visible(expr), count(NatModelKt.natAsciiText(expr), ANGL) == in);
        boolean cursorOk = true;
        for (int c = 0; c <= expr.length(); c++) {
            String pc = flat(NatCursorKt.buildNatCursor(expr, c));
            if (count(pc, ANGL) != in) { cursorOk = false; break; }
        }
        check("全光标位∠个数: " + visible(expr), cursorOk);
        check("natLinear往返∠个数: " + visible(expr), count(NatCursorKt.natLinearOf(expr), ANGL) == in);
    }

    static String flat(io.paimon.fx991.ui.Nat n) {
        return NatModelKt.natAscii(n).getLines().stream().reduce((a, b) -> a + "\n" + b).orElse("");
    }

    static String visible(String s) { return s.replace(ANGL, "<ANG>"); }

    /** 模拟 ViewModel.insertTemplate：插入文本后光标回退 back 格 */
    static int cursorAfterTpl(String before, int cur, String text, int back) {
        EditResult r = CursorModel.INSTANCE.insert(before, cur, text);
        return Math.max(0, Math.min(r.getCursor() - back, r.getText().length()));
    }

    public static void main(String[] args) {
        // ===================================================================
        System.out.println("-- 1. ∠ 投影不变量（修「∠ 一次只能写一个」的护栏）--");
        String[] angleCases = {
                "1∠2", "2∠30×3", "9∠60+5∠6", "1∠2+3∠4+5∠6",
                "9∠", "9∠60+5∠", "∠", "5∠", "1∠2∠3",
                "x∠2", "A∠B", "sin(30)∠60", "√2∠45", "(1+2)∠90−1",
                "2∠(30+15)", "1∠2.5", "0∠0", "Ans∠60", "9∠60−5∠6",
        };
        for (String e : angleCases) angleInvariant(e);

        // ===================================================================
        System.out.println("-- 2. 新模板投影：int 二维模板 / deriv / sum 屏显 --");
        String integ = NatModelKt.natAsciiText("int(x^2,0,1)");
        check("int 投影含 ∫", integ.contains("∫"));
        check("int 投影含 dx", integ.contains("dx"));
        check("int 投影含被积式 x", integ.contains("x"));
        check("int 投影含上限 1 与下限 0", integ.contains("1") && integ.contains("0"));
        System.out.println("    int(x^2,0,1) 投影:\n" + integ.replace("\n", "\n    "));
        // 空槽（模板刚插入时的样子）
        String emptyInteg = NatModelKt.natAsciiText("int(,,)");
        check("空槽 int 投影含 ∫ 与 dx", emptyInteg.contains("∫") && emptyInteg.contains("dx"));
        // natLinear 往返：模板 → 线性文本还是 int(...)
        eq("int natLinear 往返", NatCursorKt.natLinearOf("int(x^2,0,1)"), "int(x^2,0,1)");
        eq("空槽 int natLinear 往返", NatCursorKt.natLinearOf("int(,,)"), "int(,,)");
        // 屏显别名
        check("deriv 屏显 d/dx", NatModelKt.natAsciiText("deriv(x^2,1)").contains("d/dx"));
        check("sum 屏显 Σ", NatModelKt.natAsciiText("sum(x,1,10)").contains("Σ"));
        // 光标落进积分模板空槽：int(,,) 光标 4 → body 槽
        String path = NatCursorKt.natCursorPath(NatCursorKt.buildNatCursor("int(,,)", 4));
        check("int(,,) 光标4 落 body 槽 (path=" + path + ")", path.contains("body"));
        path = NatCursorKt.natCursorPath(NatCursorKt.buildNatCursor("int(,,)", 5));
        check("int(,,) 光标5 落 lo 槽 (path=" + path + ")", path.contains("lo"));
        path = NatCursorKt.natCursorPath(NatCursorKt.buildNatCursor("int(,,)", 6));
        check("int(,,) 光标6 落 hi 槽 (path=" + path + ")", path.contains("hi"));

        // ===================================================================
        System.out.println("-- 3. 模板插入后的光标落点（insert + back 语义）--");
        eq("solve() 光标落括号内", cursorAfterTpl("", 0, "solve()", 1), 6);
        eq("calc() 光标落括号内", cursorAfterTpl("", 0, "calc()", 1), 5);
        eq("int(,,) 光标落第 1 槽", cursorAfterTpl("", 0, "int(,,)", 3), 4);
        eq("deriv(,) 光标落第 1 槽", cursorAfterTpl("", 0, "deriv(,)", 2), 6);
        eq("sum(,,) 光标落第 1 槽", cursorAfterTpl("", 0, "sum(,,)", 3), 4);
        eq("lim(,) 光标落第 1 槽", cursorAfterTpl("", 0, "lim(,)", 2), 4);
        eq("dms(,,) 光标落第 1 槽", cursorAfterTpl("", 0, "dms(,,)", 3), 4);
        eq("pol(,) 光标落第 1 槽", cursorAfterTpl("", 0, "pol(,)", 2), 4);
        eq("rec(,) 光标落第 1 槽", cursorAfterTpl("", 0, "rec(,)", 2), 4);
        eq("ranint(,) 光标落第 1 槽", cursorAfterTpl("", 0, "ranint(,)", 2), 7);
        eq("sto(,) 光标落第 1 槽", cursorAfterTpl("", 0, "sto(,)", 2), 4);
        eq("const() 光标落括号内", cursorAfterTpl("", 0, "const()", 1), 6);
        eq("conv(,,) 光标落第 1 槽", cursorAfterTpl("", 0, "conv(,,)", 3), 5);
        eq("si(,) 光标落第 1 槽", cursorAfterTpl("", 0, "si(,)", 2), 3);
        eq("sinh() 光标落括号内", cursorAfterTpl("", 0, "sinh()", 1), 5);
        // 中间插入：3 + □ 处插 solve()
        EditResult mid = CursorModel.INSTANCE.insert("3+", 2, "solve()");
        eq("中段插入文本", mid.getText(), "3+solve()");
        eq("中段插入光标", Math.max(0, mid.getCursor() - 1), 8);
        // 退格原子：solve() 光标在括号内时退格先删 ( 右侧？不——光标前是 '('，逐字符；删掉整原子靠光标在调用后
        EditResult bs = CursorModel.INSTANCE.backspace("solve()", 6);
        eq("solve( 原子退格", bs.getText(), ")");

        // ===================================================================
        System.out.println("-- 4. 主行求值：新调用各一组（复用 NumericOps / PolarForm / …）--");
        near("int(x^2,0,1) = 1/3", re("int(x^2,0,1)", RAD), 1.0 / 3.0, 1e-9);
        near("int(sin(x),0,π) = 2", re("int(sin(x),0,π)", RAD), 2.0, 1e-9);
        near("deriv(x^3,2) = 12", re("deriv(x^3,2)", RAD), 12.0, 1e-6);
        near("deriv(sin(x),0) = 1", re("deriv(sin(x),0)", RAD), 1.0, 1e-8);
        near("sum(x,1,10) = 55", re("sum(x,1,10)", RAD), 55.0, 1e-12);
        near("sum(x^2,1,3) = 14", re("sum(x^2,1,3)", RAD), 14.0, 1e-12);
        near("lim(sin(x)/x,0) = 1", re("lim(sin(x)/x,0)", RAD), 1.0, 1e-6);
        near("calc(1+1) = 2", re("calc(1+1)", RAD), 2.0, 1e-12);
        Map<String, Double> vars = new HashMap<>();
        vars.put("x", 3.0);
        near("calc(x^2) x=3 → 9", re("calc(x^2)", RAD, vars), 9.0, 1e-12);
        near("dms(1,30,0) = 1.5", re("dms(1,30,0)", DEG), 1.5, 1e-12);
        near("dms(0,0,90) = 0.025", re("dms(0,0,90)", DEG), 0.025, 1e-12);
        ComplexNum p = cx("pol(1,1)", DEG);
        near("pol(1,1) 值不变(实部)", p.getRe().toDouble(), 1.0, 1e-12);
        near("pol(1,1) 值不变(虚部)", p.getIm().toDouble(), 1.0, 1e-12);
        Unified.evaluate("pol(1,1)", DEG, 0.0, 0.0, null, 0.0, new HashMap<String, Double>(), null, null);
        check("pol 附注给 r/θ", Unified.getLastNote().contains("r = ") && Unified.getLastNote().contains("θ = "));
        ComplexNum rc = cx("rec(2,60)", DEG);
        near("rec(2,60) 实部 = 1", rc.getRe().toDouble(), 1.0, 1e-12);
        near("rec(2,60) 虚部 = √3", rc.getIm().toDouble(), Math.sqrt(3.0), 1e-12);
        boolean ranOk = true;
        for (int i = 0; i < 40; i++) {
            double v = re("ranint(1,6)", RAD);
            if (v < 1 || v > 6 || v != Math.floor(v)) ranOk = false;
        }
        check("ranint(1,6) ×40 都在 [1,6] 且为整数", ranOk);
        near("const(g) = 9.80665", re("const(g)", RAD), 9.80665, 1e-12);
        near("const(c) = 299792458", re("const(c)", RAD), 299792458.0, 1e-1);
        near("const(NA)", re("const(NA)", RAD), 6.02214076e23, 1e14);
        near("2×const(g)", re("2×const(g)", RAD), 2 * 9.80665, 1e-12);
        near("si(1500,k) = 1.5", re("si(1500,k)", RAD), 1.5, 1e-12);
        near("si(0.002,m) = 2", re("si(0.002,m)", RAD), 2.0, 1e-12);
        near("conv(5,km,mi) ≈ 3.10686", re("conv(5,km,mi)", RAD), 5.0 * 1000.0 / 1609.344, 1e-9);
        near("conv(1,kg,lb) ≈ 2.20462", re("conv(1,kg,lb)", RAD), 1.0 / 0.45359237, 1e-9);
        near("conv(100,C,F) = 212", re("conv(100,C,F)", RAD), 212.0, 1e-9);
        near("conv(32,F,C) = 0", re("conv(32,F,C)", RAD), 0.0, 1e-9);
        near("conv(0,C,K) = 273.15", re("conv(0,C,K)", RAD), 273.15, 1e-9);
        near("conv(2,m,cm) = 200", re("conv(2,m,cm)", RAD), 200.0, 1e-9);
        // 错误提示
        try {
            re("const(xx)", RAD);
            check("未知常数报语法错误", false);
        } catch (Exception e) {
            check("未知常数报语法错误", e.getClass().getSimpleName().equals("CalcSyntaxError")
                    && String.valueOf(e.getMessage()).contains("未知科学常数"));
        }
        try {
            re("conv(1,km,kg)", RAD);
            check("量纲不一致报语法错误", false);
        } catch (Exception e) {
            check("量纲不一致报语法错误", e.getClass().getSimpleName().equals("CalcSyntaxError")
                    && String.valueOf(e.getMessage()).contains("量纲不一致"));
        }
        try {
            re("si(1,ww)", RAD);
            check("未知前缀报语法错误", false);
        } catch (Exception e) {
            check("未知前缀报语法错误", e.getClass().getSimpleName().equals("CalcSyntaxError"));
        }
        // 嵌套：int 的被积式里用 const / 外部参与运算
        near("int(const(g)×x,0,2) = 2g", re("int(const(g)×x,0,2)", RAD), 2 * 9.80665, 1e-6);
        near("1+int(x,0,1) = 1.5", re("1+int(x,0,1)", RAD), 1.5, 1e-9);

        // ===================================================================
        System.out.println("-- 5. solve / sto 外壳拆解 --");
        eq("unwrapSolve 带等号", InlineFuncs.unwrapSolve("solve(2x+3=7)"), "2x+3=7");
        eq("unwrapSolve 不带等号补=0", InlineFuncs.unwrapSolve("solve(x^2-3x+2)"), "x^2-3x+2=0");
        eq("unwrapSolve 非整串包装", InlineFuncs.unwrapSolve("solve(x)+1"), null);
        eq("unwrapSolve 空白", InlineFuncs.unwrapSolve("solve()"), null);
        eq("unwrapSolve 非 solve", InlineFuncs.unwrapSolve("1+1"), null);
        eq("unwrapSolve 嵌套括号", InlineFuncs.unwrapSolve("solve(sin(x)=0.5)"), "sin(x)=0.5");
        eq("parseSto 基本", InlineFuncs.parseSto("sto(5+3,A)"), new kotlin.Pair<>("5+3", "A"));
        eq("parseSto 嵌套逗号", InlineFuncs.parseSto("sto(mean(1,2),B)"), new kotlin.Pair<>("mean(1,2)", "B"));
        eq("parseSto 小写归一", InlineFuncs.parseSto("sto(1,b)"), new kotlin.Pair<>("1", "B"));
        eq("parseSto x 变量", InlineFuncs.parseSto("sto(1+1,x)"), new kotlin.Pair<>("1+1", "x"));
        eq("parseSto M 存储器", InlineFuncs.parseSto("sto(9,m)"), new kotlin.Pair<>("9", "M"));
        eq("parseSto 非法变量", InlineFuncs.parseSto("sto(1,Q)"), null);
        eq("parseSto 缺逗号", InlineFuncs.parseSto("sto(1)"), null);
        eq("parseSto 空式子", InlineFuncs.parseSto("sto(,A)"), null);

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
