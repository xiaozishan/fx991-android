import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.ui.Key;
import io.paimon.fx991.ui.KeyAction;
import io.paimon.fx991.ui.KeysKt;
import io.paimon.fx991.ui.SubKey;
import java.util.List;

/**
 * 批次 K4 键位回归：把「参照截图目标表」整体钉死。
 * 每个键断言：主字 / SHIFT 层（字+动作）/ ALPHA 层（字+动作）。
 * 以后再整体错位一行，这套测试会立刻红。
 * 另附 K4 新函数（cot/acot/gcd/lcm/mod/ceil/floor/∞）的引擎断言。
 */
public class KeymapTest {

    static int pass = 0;
    static int fail = 0;

    static final String MINUS = "−";   // −
    static final String MUL = "×";     // ×
    static final String DIV = "÷";     // ÷
    static final String SQ2 = "²";     // ²
    static final String SQ3 = "³";     // ³
    static final String RECIP = "⁻¹";  // ⁻¹
    static final String SQRT = "√";    // √
    static final String PI = "π";      // π
    static final String INF = "∞";     // ∞
    static final String ANG = "∠";     // ∠
    static final String SWAP = "x⇄y";  // x⇄y
    static final String XD = "xʸ";     // xʸ
    static final String XRY = "ˣ√y";   // ˣ√y
    static final String TENX = "10ˣ";  // 10ˣ
    static final String EX = "eˣ";     // eˣ
    static final String CUBERT = "³√x"; // ∛x
    static final String LOGXY = "logₓy"; // logₓy
    static final String INTG = "∫dx";  // ∫dx
    static final String SD = "S⇔D";    // S⇔D
    static final String DMS = "°′″";   // °′″

    /** 动作描述子：ins:文本 / func:KIND / screen:NAME / 单例类名 */
    static String act(KeyAction a) {
        if (a == null) return "";
        if (a instanceof KeyAction.Insert) return "ins:" + ((KeyAction.Insert) a).getText();
        if (a instanceof KeyAction.OpenFunc) return "func:" + ((KeyAction.OpenFunc) a).getKind().name();
        if (a instanceof KeyAction.GoScreen) return "screen:" + ((KeyAction.GoScreen) a).getScreen().name();
        return a.getClass().getSimpleName();
    }

    static void check(String where, Object got, Object expect) {
        boolean good = expect == null ? got == null : expect.equals(got);
        if (good) pass++; else fail++;
        if (!good) System.out.println("  FAIL  " + where + " => " + got + " (expected " + expect + ")");
    }

    /** 一个键的完整断言：主字 / 主动作 / SHIFT 字+动作 / ALPHA 字+动作 */
    static void key(List<List<Key>> rows, int r, int c,
                    String label, String main,
                    String sLabel, String sAct,
                    String aLabel, String aAct) {
        Key k = rows.get(r).get(c);
        String where = "R" + r + "C" + c + "[" + label + "]";
        check(where + ".label", k.getLabel(), label);
        check(where + ".action", act(k.getAction()), main);
        SubKey s = k.getShift();
        check(where + ".shift.label", s == null ? "" : s.getLabel(), sLabel);
        check(where + ".shift.action", s == null ? "" : act(s.getAction()), sAct);
        SubKey a = k.getAlpha();
        check(where + ".alpha.label", a == null ? "" : a.getLabel(), aLabel);
        check(where + ".alpha.action", a == null ? "" : act(a.getAction()), aAct);
        if (fail == 0 || true) { /* 计数即断言 */ }
        if (k.getLabel().equals(label) && act(k.getAction()).equals(main)
                && (s == null ? "" : s.getLabel()).equals(sLabel)
                && (s == null ? "" : act(s.getAction())).equals(sAct)
                && (a == null ? "" : a.getLabel()).equals(aLabel)
                && (a == null ? "" : act(a.getAction())).equals(aAct)) {
            System.out.println("  PASS  " + where);
        }
    }

    static void ok(String expr, AngleMode m, String expect) {
        String got;
        try {
            double v = CalcEngine.INSTANCE.evaluate(expr, m, 0, 0);
            got = CalcEngine.INSTANCE.format(v);
        } catch (Throwable e) {
            got = "ERR:" + e.getClass().getSimpleName();
        }
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(expr, 22) + " => " + pad(got, 14) + (good ? "" : "(expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    public static void main(String[] args) {
        AngleMode DEG = AngleMode.DEG;
        List<List<Key>> rows = KeysKt.keypadRows();

        System.out.println("-- 结构：9 行，列数 5/4/6/6/6/5/5/5/5 --");
        check("rows", rows.size(), 9);
        int[] cols = {5, 4, 6, 6, 6, 5, 5, 5, 5};
        for (int i = 0; i < cols.length; i++) check("row" + i + ".cols", rows.get(i).size(), cols[i]);

        System.out.println("-- 第 2 排：SHIFT · ALPHA · 方向键 · MODE · 2nd --");
        key(rows, 0, 0, "SHIFT", "Shift", "", "", "", "");
        key(rows, 0, 1, "ALPHA", "Alpha", "", "", "", "");
        key(rows, 0, 2, "", "PadOk", "", "", "", "");
        key(rows, 0, 3, "MODE", "Mode", "", "", "", "");
        key(rows, 0, 4, "2nd", "Shift", "", "", "", "");

        System.out.println("-- 第 3 排：CALC · ∫dx · x⁻¹ · logₓy --");
        key(rows, 1, 0, "CALC", "func:CALC", "SOLVE", "func:SOLVE", "=", "ins:=");
        key(rows, 1, 1, INTG, "func:INTEGRAL", "d/dx", "func:DERIV", ";", "ins:;");
        key(rows, 1, 2, "x" + RECIP, "ins:" + RECIP + "(", "x!", "ins:!", "", "");
        key(rows, 1, 3, LOGXY, "ins:logb(", "Σ", "func:SUMMATION", "", "");

        System.out.println("-- 第 4 排：x/y · √x · x² · xʸ · log · ln --");
        key(rows, 2, 0, "x/y", "Fraction", "ab/c", "FracFormat", "", "");
        key(rows, 2, 1, SQRT + "x", "ins:" + SQRT + "(", CUBERT, "ins:cbrt(", "mod", "ins:mod(");
        key(rows, 2, 2, "x" + SQ2, "ins:" + SQ2, "x" + SQ3, "ins:" + SQ3, "", "");
        key(rows, 2, 3, XD, "ins:^", XRY, "ins:root(", "", "");
        key(rows, 2, 4, "log", "ins:log(", TENX, "ins:10^", "", "");
        key(rows, 2, 5, "ln", "ins:ln(", EX, "ins:exp(", "", "");

        System.out.println("-- 第 5 排：(−) · °′″ · hyp · sin · cos · tan --");
        key(rows, 3, 0, "(" + MINUS + ")", "SignToggle", ANG, "ins:" + ANG, "a", "ins:A");
        key(rows, 3, 1, DMS, "func:DMS", "FACT", "ins:!", "b", "ins:B");
        key(rows, 3, 2, "hyp", "func:HYPER", "|x|", "ins:abs(", "c", "ins:C");
        key(rows, 3, 3, "sin", "ins:sin(", "sin" + RECIP, "ins:sin" + RECIP + "(", "d", "ins:D");
        key(rows, 3, 4, "cos", "ins:cos(", "cos" + RECIP, "ins:cos" + RECIP + "(", "e", "ins:E");
        key(rows, 3, 5, "tan", "ins:tan(", "tan" + RECIP, "ins:tan" + RECIP + "(", "f", "ins:F");

        System.out.println("-- 第 6 排：RCL · ENG · ( · ) · S⇔D · M+ --");
        key(rows, 4, 0, "RCL", "Mrc", "STO", "OpenSto", "CLRv", "ClrVars");
        key(rows, 4, 1, "ENG", "EngToggle", "i", "ins:i", "Cot", "ins:cot(");
        key(rows, 4, 2, "(", "ins:(", "%", "ins:%", "Cot" + RECIP, "ins:acot(");
        key(rows, 4, 3, ")", "ins:)", ",", "ins:,", "x", "ins:x");
        key(rows, 4, 4, SD, "Sd", SWAP, "SwapXY", "y", "ins:y");
        key(rows, 4, 5, "M+", "MPlus", "M" + MINUS, "MMinus", "m", "ins:M");

        System.out.println("-- 第 7 排：7 8 9 ⌫ AC --");
        key(rows, 5, 0, "7", "ins:7", "CONST", "OpenConst", "", "");
        key(rows, 5, 1, "8", "ins:8", "CONV", "OpenConv", "SI", "OpenSi");
        key(rows, 5, 2, "9", "ins:9", "Limit", "func:LIMIT", INF, "ins:" + INF);
        key(rows, 5, 3, "", "Del", "", "", "", "");
        key(rows, 5, 4, "AC", "Ac", "CLR ALL", "ClrAll", "", "");

        System.out.println("-- 第 8 排：4 5 6 × ÷ --");
        key(rows, 6, 0, "4", "ins:4", "MATRIX", "screen:MATRIX", "", "");
        key(rows, 6, 1, "5", "ins:5", "VECTOR", "screen:VECTOR", "", "");
        key(rows, 6, 2, "6", "ins:6", "FUNC", "screen:FUNC_HELP", "HELP", "screen:FUNC_HELP");
        key(rows, 6, 3, MUL, "ins:" + MUL, "nPr", "ins:npr(", "GCD", "ins:gcd(");
        key(rows, 6, 4, DIV, "ins:" + DIV, "nCr", "ins:ncr(", "LCM", "ins:lcm(");

        System.out.println("-- 第 9 排：1 2 3 + − --");
        key(rows, 7, 0, "1", "ins:1", "STAT", "screen:STAT", "", "");
        key(rows, 7, 1, "2", "ins:2", "CMPLX", "screen:CMPLX", "", "");
        key(rows, 7, 2, "3", "ins:3", "DISTR", "screen:DISTR", "", "");
        key(rows, 7, 3, "+", "ins:+", "Pol", "func:POL", "Ceil", "ins:ceil(");
        key(rows, 7, 4, MINUS, "ins:" + MINUS, "Rec", "func:REC", "Floor", "ins:floor(");

        System.out.println("-- 第 10 排：0 . Exp Ans = --");
        key(rows, 8, 0, "0", "ins:0", "COPY", "CopyExpr", "PASTE", "PasteExpr");
        key(rows, 8, 1, ".", "ins:.", "Ran#", "RandomInsert", "RanInt", "func:RANINT");
        key(rows, 8, 2, "Exp", "ins:" + MUL + "10^", PI, "ins:" + PI, "e", "ins:e");
        key(rows, 8, 3, "Ans", "ins:Ans", "", "", "PreAns", "ins:PreAns");
        key(rows, 8, 4, "=", "Equals", "History", "OpenHistory", "", "");

        System.out.println("-- K4 新函数：引擎行为 --");
        ok("gcd(12,18)", DEG, "6");
        ok("gcd(" + MINUS + "12,18)", DEG, "6");
        ok("lcm(4,6)", DEG, "12");
        ok("lcm(3,5)", DEG, "15");
        ok("mod(7,3)", DEG, "1");
        ok("mod(" + MINUS + "7,3)", DEG, "2");
        ok("ceil(2.3)", DEG, "3");
        ok("ceil(" + MINUS + "2.3)", DEG, "-2");
        ok("floor(2.7)", DEG, "2");
        ok("floor(" + MINUS + "2.3)", DEG, "-3");
        ok("cot(45)", DEG, "1");
        ok("acot(1)", DEG, "45");
        ok("acot(0)", DEG, "90");
        ok("1" + DIV + INF, DEG, "0");
        ok("gcd(2.5,3)", DEG, "ERR:CalcMathError");
        ok("mod(7,0)", DEG, "ERR:CalcMathError");
        ok("lcm(6,8)+gcd(12,18)", DEG, "30");
        ok("200+10%", DEG, "220");

        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
