import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;
import io.paimon.fx991.engine.OcrText;

import java.util.Arrays;
import java.util.List;

/**
 * 批次 F 回归：本地离线 OCR 的纯逻辑部分（engine/OcrText.kt）。
 * 覆盖：多行合并 / 全角转半角复用 normalizeExpr / 根号与等号等 OCR 专有符号修正 /
 * 小数点误识别（。/· 在数字间 → .）/ 易混字符纠正（O/o→0、l/I/|→1，仅数字上下文）/
 * 故意不纠正的边界（单独字母、逗号、x 变量）/ 清洗结果端到端可被引擎求值。
 * ML Kit 本体依赖 Android 运行时，无法 JVM 测，见 README 说明。
 */
public class OcrTextTest {

    static int pass = 0;
    static int fail = 0;

    static void eq(String what, Object got, Object expect) {
        boolean good = String.valueOf(got).equals(String.valueOf(expect));
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
    }

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static void near(String what, double got, double expect) {
        boolean good = Math.abs(got - expect) < 1e-9;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static double eval(String expr) {
        return CalcEngine.INSTANCE.evaluate(expr, AngleMode.DEG, 0.0, 0.0, 0.0,
                java.util.Collections.<String, Double>emptyMap());
    }

    public static void main(String[] args) {
        OcrText ocr = OcrText.INSTANCE;

        // ---- ① 多行 / 多块合并 ----
        eq("两行拼接", ocr.mergeLines(Arrays.asList("12+3", "×4")), "12+3×4");
        eq("三行拼接", ocr.mergeLines(Arrays.asList("(1+2)", "×", "3")), "(1+2)×3");
        eq("空行被丢弃", ocr.mergeLines(Arrays.asList("12+3", "", "  ", "×4")), "12+3×4");
        eq("行首尾空白被去掉", ocr.mergeLines(Arrays.asList("  12+3 ", " ×4  ")), "12+3×4");
        eq("全空行合并为空", ocr.mergeLines(Arrays.asList("", "  ")), "");
        eq("clean 内部按换行合并", ocr.clean("12+3\n×4"), "12+3×4");
        eq("cleanLines 等价", ocr.cleanLines(Arrays.asList("12+3", "×4")), "12+3×4");
        eq("空白输入返回空串", ocr.clean("   \n  "), "");
        eq("cleanLines 空列表", ocr.cleanLines(java.util.Collections.<String>emptyList()), "");

        // ---- ② 全角 → 半角（复用 PhotoSolve.normalizeExpr）----
        eq("全角数字", ocr.clean("１２＋３４"), "12+34");
        eq("全角减号与括号", ocr.clean("（５－２）×３"), "(5-2)×3");
        eq("全角星号归一为乘号", ocr.clean("６＊７"), "6×7");
        eq("全角斜杠归一为除号", ocr.clean("８／２"), "8÷2");
        eq("全角空格丢弃", ocr.clean("１２　＋　３"), "12+3");

        // ---- ③ OCR 专有符号修正 ----
        eq("✓ 误识别为根号", ocr.clean("✓(4)"), "√(4)");
        eq("✔ 误识别为根号", ocr.clean("✔9"), "√9");
        eq("∨ 误识别为根号", ocr.clean("∨(16)"), "√(16)");
        eq("全角等号", ocr.clean("x＋1＝2"), "x+1=2");
        eq("数学减号 U+2212", ocr.clean("5−2"), "5-2");
        eq("中文数字零", ocr.clean("1〇2"), "102");
        eq("ASCII 乘号归一", ocr.clean("6*7"), "6×7");
        eq("双星号归一为幂", ocr.clean("2**10"), "2^10");

        // ---- ④ 小数点误识别 ----
        eq("句号小数点", ocr.clean("3。14"), "3.14");
        eq("间隔号小数点", ocr.clean("3·14"), "3.14");
        eq("句号不在数字间不动", ocr.clean("3。+2"), "3+2");
        eq("逗号故意不动（函数分隔符）", ocr.clean("logb(2,8)"), "logb(2,8)");
        eq("逗号在数字间也不动", ocr.clean("1,5"), "1,5");

        // ---- ⑤ 易混字符纠正（仅数字上下文）----
        eq("大写 O 在数字间→0", ocr.clean("1O2"), "102");
        eq("小写 o 在数字间→0", ocr.clean("2o5"), "205");
        eq("O 右邻小数点→0", ocr.clean("O.5"), "0.5");
        eq("O 左邻数字→0", ocr.clean("5O"), "50");
        eq("小写 l 右邻数字→1", ocr.clean("l2+3"), "12+3");
        eq("大写 I 左邻数字→1", ocr.clean("5I"), "51");
        eq("竖线 | 在数字间→1", ocr.clean("2|3"), "213");
        eq("l 两侧都是数字→1", ocr.clean("3l4"), "314");

        // ---- ⑥ 故意不纠正（过度纠正会改变原意）----
        eq("单独的 O 保留", ocr.clean("O+2"), "O+2");
        eq("单独的 l 保留", ocr.clean("l+2"), "l+2");
        eq("log 里的 l/o 不动", ocr.clean("log(100)"), "log(100)");
        eq("sin 不动", ocr.clean("sin(30)"), "sin(30)");
        eq("变量 x 不动", ocr.clean("3x+1=5"), "3x+1=5");
        eq("S 不改成 5", ocr.clean("2S"), "2S");
        eq("Z 不改成 2", ocr.clean("Z+1"), "Z+1");

        // ---- ⑦ 端到端：清洗结果可被引擎直接求值 ----
        near("1O2+3 → 105", eval(ocr.clean("1O2+3")), 105.0);
        near("3。14×2 → 6.28", eval(ocr.clean("3。14×2")), 6.28);
        near("✓(9)+l2 → 15", eval(ocr.clean("✓(9)+l2")), 15.0);
        near("全角整行求值", eval(ocr.clean("（５－２）×３")), 9.0);
        near("多行合并求值 12+3×4=24", eval(ocr.clean("12+3\n×4")), 24.0);
        near("2**1O → 1024", eval(ocr.clean("2**1O")), 1024.0);
        near("√ 变体求值", eval(ocr.clean("∨(16)")), 4.0);
        near("百分号求值", eval(ocr.clean("5O％")), 0.5);

        System.out.println("RESULT OcrTextTest pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
