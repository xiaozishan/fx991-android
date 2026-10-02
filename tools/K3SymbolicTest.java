import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.InlineFuncs;
import io.paimon.fx991.engine.SolveResult;
import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.NatCursorKt;
import io.paimon.fx991.ui.NatModelKt;

/**
 * 批次 K3-symbolic 回归：
 *  A. solve(...) 符号解 —— 括号内允许等号、外面按 = 出解、符号解优先、复数解、旧行为兼容
 *     （模拟 ViewModel.evaluateNow 路径：unwrapSolve 剥壳 → EquationSolver.solve）
 *  B. 方向键上下 = 二维结构内垂直移光标（分数分子↔分母、上标 ▲进指数 / ▼出到基线、
 *     根号内外、∫上下限；线性表达式与结构边缘位 ↑→首 / ↓→尾；槽位内没有去处 → 原地不动）
 */
public class K3SymbolicTest {

    static int pass = 0;
    static int fail = 0;

    static final AngleMode DEG = AngleMode.DEG;
    static final AngleMode RAD = AngleMode.RAD;

    static void check(String what, boolean good) {
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ") + what);
    }

    static void eq(String what, Object got, Object expect) {
        boolean good = expect == null ? got == null : expect.equals(got);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " => " + got + (good ? "" : " (expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    /** 模拟按 = ：solve 外壳剥掉（无等号补 =0）→ 求解 */
    static SolveResult pressEquals(String expr, AngleMode m) {
        String inner = InlineFuncs.unwrapSolve(expr);
        String target = inner != null ? inner : expr;
        return EquationSolver.INSTANCE.solve(target, m);
    }

    static String solveText(String expr, AngleMode m) {
        try {
            return pressEquals(expr, m).getText();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    static int up(String expr, int cur) { return CursorModel.INSTANCE.moveUp(expr, cur); }
    static int down(String expr, int cur) { return CursorModel.INSTANCE.moveDown(expr, cur); }

    public static void main(String[] args) {
        System.out.println("-- A1. solve(...) 带等号：符号解优先、多解全列 --");
        eq("solve(2x+3=7)", solveText("solve(2x+3=7)", DEG), "x = 2");
        eq("solve(x^2=4)", solveText("solve(x^2=4)", DEG), "x = -2, 2");
        eq("solve(x^2=2)", solveText("solve(x^2=2)", DEG), "x = ±√2");
        eq("solve(3x+1=5) 精确分数", solveText("solve(3x+1=5)", DEG), "x = 4/3");
        eq("solve(x^2-3x+2=0)", solveText("solve(x^2-3x+2=0)", DEG), "x = 1, 2");
        eq("solve(x^2−3x+2=0) U+2212", solveText("solve(x^2−3x+2=0)", DEG), "x = 1, 2");
        // 多解可逐条插入
        SolveResult r4 = pressEquals("solve(x^2=4)", DEG);
        eq("x^2=4 两条解", r4.getItems().size(), 2);
        eq("x^2=4 解①可插入 -2", r4.getItems().get(0).getInsert(), "-2");
        eq("x^2=4 解②可插入 2", r4.getItems().get(1).getInsert(), "2");

        System.out.println();
        System.out.println("-- A2. 复数解（判别式 < 0 不再报「无解」）--");
        eq("solve(x^2+1=0) → ±i", solveText("solve(x^2+1=0)", DEG), "x = ±i");
        eq("solve(x^2=-1) → ±i", solveText("solve(x^2=-1)", DEG), "x = ±i");
        eq("solve(-x^2=1) → ±i（负首项系数不出 ±−i）", solveText("solve(-x^2=1)", DEG), "x = ±i");
        eq("solve(x^2+2x+5=0) → -1±2i", solveText("solve(x^2+2x+5=0)", DEG), "x = -1 ± 2i");
        eq("solve(x^2+x+1=0) → (-1±√3i)/2", solveText("solve(x^2+x+1=0)", DEG), "x = (-1 ± √3i)/2");
        eq("solve(x^3=1) → 1, (-1±√3i)/2", solveText("solve(x^3=1)", DEG), "x = 1, (-1 ± √3i)/2");
        eq("solve(x^4=1) → 1, -1, ±i", solveText("solve(x^4=1)", DEG), "x = 1, -1, ±i");
        eq("solve(2x^2+2x+2=0) 同分母合并", solveText("solve(2x^2+2x+2=0)", DEG), "x = (-1 ± √3i)/2");
        // 复数解也能算对（代回验证）：x = (-1±√3i)/2 是 x²+x+1=0 的根 —— 由符号公式保证，这里验文本形态
        check("复根标注 ± 与 i", solveText("solve(x^3-1=0)", DEG).contains("±")
                && solveText("solve(x^3-1=0)", DEG).contains("i"));

        System.out.println();
        System.out.println("-- A3. 兼容旧行为 + 超越方程 + 近似标注 --");
        eq("solve(2x+3) 仍按 f(x)=0", solveText("solve(2x+3)", DEG), "x = -3/2");
        String sx = solveText("solve(sin(x)=0.5)", DEG);
        check("超越方程 sin(x)=0.5 含 30°", sx.contains("30°"));
        check("超越方程 sin(x)=0.5 含 150°", sx.contains("150°"));
        check("超越方程给数值解说明", pressEquals("solve(sin(x)=0.5)", DEG).getNote().contains("数值解"));
        // 根式过大 → 数值回退 + 标明近似
        SolveResult big = pressEquals("solve(x^2=2000000000000000000)", DEG);
        check("大根式回退数值仍出解", big.getKind().toString().equals("SOLUTIONS"));
        check("大根式标明是近似", big.getNote().contains("近似"));

        System.out.println();
        System.out.println("-- B1. 上下 = 分数 分子 ↔ 分母（等距列映射）--");
        eq("1÷2 分子首 ↓→分母首", down("1÷2", 0), 2);
        eq("1÷2 分子尾 ↓→分母尾", down("1÷2", 1), 3);
        eq("1÷2 分母首 ↑→分子首", up("1÷2", 2), 0);
        eq("1÷2 分母尾 ↑→分子尾", up("1÷2", 3), 1);
        eq("12÷34 分子第2列 ↓→分母第2列", down("12÷34", 1), 4);
        eq("12÷34 分子尾 ↓→分母尾", down("12÷34", 2), 5);
        eq("12÷34 分母第2列 ↑→分子第2列", up("12÷34", 4), 1);
        // x⁻¹ 在节点树里是整体 Sym（不是分数结构）→ 走线性兜底：↓→尾 / ↑→首
        eq("x⁻¹ 无二维结构 ↓→尾", down("x⁻¹", 1), 3);
        eq("x⁻¹ 无二维结构 ↑→首", up("x⁻¹", 0), 0);

        System.out.println();
        System.out.println("-- B2. 上下 = 上标 ↔ 基线 --");
        eq("x^2 底数首 ↑→指数首", up("x^2", 0), 2);
        eq("x^2 底数尾 ↑→指数尾", up("x^2", 1), 3);
        // K3-symbolic 修复：↓ 从指数「出」到整个上标块之后的基线位，
        // 不再落进底数内部（旧行为：x^2 ▼ 后打 +3 会错成 x+3^2）
        eq("x^2 指数首 ↓→出到块尾", down("x^2", 2), 3);
        eq("x^2 指数尾 ↓→留在块尾", down("x^2", 3), 3);
        eq("x^2 指数上再 ↑→原地", up("x^2", 3), 3);
        eq("3² 底数 ↑→指数尾", up("3²", 1), 2);
        eq("3² 指数 ↓→出到块尾", down("3²", 1), 2);

        System.out.println();
        System.out.println("-- B3. 上下 = 根号内外 / ∫ 上下限 --");
        eq("√(x)+1 √内 ↑→出到根号后", up("√(x)+1", 3), 4);
        eq("√(x)+1 根号后 ↓→回 √ 内尾", down("√(x)+1", 4), 3);
        eq("√(x)+1 √内 ↓→原地", down("√(x)+1", 2), 2);
        eq("int(x,0,1) 被积式 ↑→上限", up("int(x,0,1)", 5), 9);
        eq("int(x,0,1) 被积式 ↓→下限", down("int(x,0,1)", 5), 7);
        eq("int(x,0,1) 下限 ↑→上限", up("int(x,0,1)", 7), 9);
        eq("int(x,0,1) 上限 ↓→下限", down("int(x,0,1)", 9), 7);
        eq("int(,,) 空槽被积式 ↓→下限槽", down("int(,,)", 4), 5);
        eq("int(,,) 空槽下限 ↑→上限槽", up("int(,,)", 5), 6);
        eq("int(,,) 空槽上限 ↓→下限槽", down("int(,,)", 6), 5);

        System.out.println();
        System.out.println("-- B4. 嵌套与线性兜底 --");
        eq("1÷(2+x^2) 嵌套：指数内 ↓ 出到内层上标块尾", down("1÷(2+x^2)", 8), 8);
        eq("1÷(2+x^2) 嵌套：底数 ↑ 进指数", up("1÷(2+x^2)", 6), 8);
        eq("1÷(2+x^2) 分母空白处 ↑→分子", up("1÷(2+x^2)", 3), 1);
        eq("线性式 123+45 ↑→开头", up("123+45", 3), 0);
        eq("线性式 123+45 ↓→末尾", down("123+45", 3), 6);
        eq("空表达式 ↑ 安全", up("", 0), 0);
        eq("空表达式 ↓ 安全", down("", 0), 0);
        eq("越界钳制安全", up("1÷2", 99), 1);

        System.out.println();
        System.out.println("-- A4. 解的自然书写渲染：± 不能丢（修结果行 x=i / −12i 误导）--");
        check("x = ±i 渲染含 ±", NatModelKt.natAsciiText("x = ±i").contains("±"));
        eq("x = ±i 渲染", NatModelKt.natAsciiText("x = ±i"), "x=±i");
        eq("x = -1 ± 2i 渲染", NatModelKt.natAsciiText("x = -1 ± 2i"), "x=−1±2i");
        check("(-1 ± √3i)/2 渲染含 ±", NatModelKt.natAsciiText("x = (-1 ± √3i)/2").contains("±"));
        eq("± natLinear 往返", NatCursorKt.natLinearOf("x = ±i"), "x=±i");
        eq("±√2 natLinear 往返（√ 规范化为 √(...)）", NatCursorKt.natLinearOf("x = ±√2"), "x=±√(2)");

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
