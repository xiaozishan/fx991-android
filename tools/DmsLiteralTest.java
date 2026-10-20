import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.CalcValue;
import io.paimon.fx991.engine.ComplexNum;
import io.paimon.fx991.engine.Unified;
import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import java.util.HashMap;

/**
 * 批次 K6：度分秒符号输入（° ′ ″）回归。
 *
 *  · 键位循环：度 → 分 → 秒；不再插入 dms(,,) 函数模板
 *  · 引擎字面量：12°34′56″ = 12 + 34/60 + 56/3600
 *  · 兼容旧 dms(度,分,秒)
 */
public class DmsLiteralTest {

    static int pass = 0;
    static int fail = 0;

    static final String DEG_CH = "°";
    static final String MIN_CH = "′";
    static final String SEC_CH = "″";

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static void eq(String what, String got, String want) {
        check(what + (got.equals(want) ? "" : "  got=[" + got + "] want=[" + want + "]"), got.equals(want));
    }

    static void near(String what, double got, double want, double eps) {
        boolean ok = Math.abs(got - want) <= eps;
        check(what + (ok ? "" : "  got=" + got + " want=" + want), ok);
    }

    static double eval(String expr) {
        return CalcEngine.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0);
    }

    /** dms() 是内联函数，只走统一求值器 */
    static double evalUnified(String expr) {
        CalcValue v = Unified.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, null, 0.0,
                new HashMap<String, Double>(), null, null);
        ComplexNum c = Unified.INSTANCE.asComplex(v);
        if (c == null) throw new IllegalStateException("非标量结果: " + expr);
        return c.getRe().toDouble();
    }

    public static void main(String[] args) {
        // ---- 键位循环 ----
        eq("空行按 °′″ → °", CursorModel.INSTANCE.insertDmsSeparator("", 0).getText(), DEG_CH);
        eq("12 之后按 → 12°", CursorModel.INSTANCE.insertDmsSeparator("12", 2).getText(), "12" + DEG_CH);
        eq("12° 再按 → 分", CursorModel.INSTANCE.insertDmsSeparator("12" + DEG_CH, 3).getText(), "12" + DEG_CH + MIN_CH);
        eq("12°34′ 再按 → 秒",
                CursorModel.INSTANCE.insertDmsSeparator("12" + DEG_CH + "34" + MIN_CH, 6).getText(),
                "12" + DEG_CH + "34" + MIN_CH + SEC_CH);
        eq("秒之后再按 → 新一轮度",
                CursorModel.INSTANCE.insertDmsSeparator("12" + DEG_CH + "34" + MIN_CH + "56" + SEC_CH, 9).getText(),
                "12" + DEG_CH + "34" + MIN_CH + "56" + SEC_CH + DEG_CH);

        // ---- 完整字面量输入 ----
        EditResult r = CursorModel.INSTANCE.insertDmsSeparator("12" + DEG_CH + "34", 5);
        eq("12°34 里插分", r.getText(), "12" + DEG_CH + "34" + MIN_CH);

        // ---- 引擎求值 ----
        near("1°30′ = 1.5", eval("1" + DEG_CH + "30" + MIN_CH), 1.5, 1e-12);
        near("1°30′30″ = 1.50833…", eval("1" + DEG_CH + "30" + MIN_CH + "30" + SEC_CH), 1.5083333333, 1e-9);
        near("90° = 90", eval("90" + DEG_CH), 90.0, 1e-12);
        near("12°34′56″", eval("12" + DEG_CH + "34" + MIN_CH + "56" + SEC_CH), 12.5822222222, 1e-9);
        near("sin(30°) = 0.5", eval("sin(30" + DEG_CH + ")"), 0.5, 1e-12);
        near("1°30′ + 0.5 = 2", eval("1" + DEG_CH + "30" + MIN_CH + "+0.5"), 2.0, 1e-12);
        near("旧函数仍可用 dms(1,30,0)", evalUnified("dms(1,30,0)"), 1.5, 1e-12);

        // ---- 容错：符号连按 / 旧输入里的 °′ 残渣不能报错 ----
        near("3.5°′ = 3.5（多按一次分）", eval("3.5" + DEG_CH + MIN_CH), 3.5, 1e-12);
        near("3.5°′″ = 3.5（多按两次）", eval("3.5" + DEG_CH + MIN_CH + SEC_CH), 3.5, 1e-12);
        near("sin⁻¹(1.5×sin(3.5°′)) 不报错",
                eval("sin\u207B\u00B9(1.5" + "\u00D7" + "sin(3.5" + DEG_CH + MIN_CH + "))"), 5.25409612, 1e-6);

        // ---- 连按分隔键：度 → 分 可以连按（中途再输数字），引擎容忍空分钟 ----
        eq("° 后连按 → 插入 ′（分）",
                CursorModel.INSTANCE.insertDmsSeparator("3.5" + DEG_CH, 4).getText(), "3.5" + DEG_CH + MIN_CH);

        System.out.println("RESULT DmsLiteralTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
