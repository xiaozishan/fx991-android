import io.paimon.fx991.ui.Caret;
import io.paimon.fx991.ui.CaretModel;
import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import io.paimon.fx991.ui.NatCursorKt;
import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;

/**
 * 光标结构层级回归（v2.1）：
 *   分数 / 指数 / 根号的内容末尾与结构边界共用同一偏移量，
 *   Caret(offset, exit) 要能把「分母末尾 → 结构右边 → 结构左边 → 分子开头」分开。
 */
public class CaretTest {
    static int pass = 0, fail = 0;

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label);
    }

    static void step(String label, String expr, int off, int exit, boolean right, int wOff, int wExit) {
        Caret c = new Caret(off, exit);
        Caret r = right ? CaretModel.INSTANCE.right(expr, c) : CaretModel.INSTANCE.left(expr, c);
        boolean ok = r.getOffset() == wOff && r.getExit() == wExit;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label + " " + (right ? "→" : "←")
                + "  (" + r.getOffset() + "," + r.getExit() + ")"
                + (ok ? "" : "   want (" + wOff + "," + wExit + ")"));
    }

    public static void main(String[] a) {
        String F = "9÷6";
        System.out.println("[分数 " + F + "]");
        step("分母末尾 → 跳出分数", F, 3, 0, true, 3, 1);
        step("分数右外侧 → 绕到左边", F, 3, 1, true, 0, 1);
        step("分数左外侧 → 进分子", F, 0, 1, true, 0, 0);
        step("分子开头 → 分子内右移", F, 0, 0, true, 1, 0);
        step("分子末尾 → 分母开头", F, 1, 0, true, 2, 0);

        step("分子开头 ← 到分数左外侧", F, 0, 0, false, 0, 1);
        step("分数左外侧 ← 绕到分数右外侧", F, 0, 1, false, 3, 1);
        step("分数右外侧 ← 回分母末尾", F, 3, 1, false, 3, 0);
        step("分母开头 ← 分子末尾", F, 2, 0, false, 1, 0);

        String P = "2^3";
        System.out.println("[指数 " + P + "]");
        step("指数末尾 → 跳出", P, 3, 0, true, 3, 1);
        step("幂右外侧 → 绕到左边", P, 3, 1, true, 0, 1);
        step("幂左外侧 → 进底数", P, 0, 1, true, 0, 0);
        step("底数末尾 → 指数开头", P, 1, 0, true, 2, 0);

        String Q = "√9";
        System.out.println("[根号 " + Q + "]");
        step("根号内末尾 → 跳出", Q, 2, 0, true, 2, 1);
        step("根号右外侧 → 绕到左边", Q, 2, 1, true, 0, 1);

        String E = "9÷6+3";
        System.out.println("[分数后接运算 " + E + "]");
        step("分母末尾 → 分数右外侧", E, 3, 0, true, 3, 1);
        step("分数右外侧 → 越过 +", E, 3, 1, true, 4, 0);

        String N = "1+2÷3+4";
        System.out.println("[两侧都有运算 " + N + "]");
        step("分母末尾 → 跳出", N, 5, 0, true, 5, 1);
        step("跳出后 → 越过 +", N, 5, 1, true, 6, 0);

        System.out.println("[线性表达式不受影响]");
        step("123 末尾 → 绕回开头", "123", 3, 0, true, 0, 0);
        step("123 开头 ← 绕到末尾", "123", 0, 0, false, 3, 0);

        nestedFractions();

        System.out.println("RESULT CaretTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    static void nestedFractions() {
        CaretModel moves = CaretModel.INSTANCE;
        CursorModel edits = CursorModel.INSTANCE;

        String fraction = "1÷(2÷3)";
        step("外层分子末尾 → 跳过隐藏左括号", fraction, 1, 0, true, 3, 1);
        step("内层分数左外侧 → 进入内层分子", fraction, 3, 1, true, 3, 0);
        step("内层分子开头 ← 退到内层左外侧", fraction, 3, 0, false, 3, 1);
        step("内层分数左外侧 ← 回到外层分子末尾", fraction, 3, 1, false, 1, 0);
        step("内层分母末尾 → 跳出内层", fraction, 6, 0, true, 6, 1);
        step("内层分数右外侧 → 跳过隐藏右括号，退出外层", fraction, 6, 1, true, 7, 1);
        step("外层右外侧 ← 回到内层右外侧", fraction, 7, 1, false, 6, 1);
        step("内层分数右外侧 ← 进入内层分母", fraction, 6, 1, false, 6, 0);
        check("进入内层起点时，光标画在内层分子里", path(fraction, 3, 0).contains("/den/Row[1]/num"));
        check("内层右外侧显示在外层分母内", path(fraction, 6, 1).contains("/den/") &&
                !path(fraction, 6, 1).endsWith("/den/Row[1]/den"));
        check("外层右外侧显示在分数横线外", !path(fraction, 7, 1).contains("/den"));
        check("点到隐藏左括号映射到内层分子开头", moves.normalize(fraction, new Caret(2, 0)).equals(new Caret(3, 0)));
        check("点到隐藏右括号映射到内层分母末尾", moves.normalize(fraction, new Caret(7, 0)).equals(new Caret(6, 0)));
        check("跳出内层后向上直接去外层分子", moves.up(fraction, new Caret(6, 1)).equals(new Caret(1, 0)));
        check("跳出整个分数后再向下仍留在外面", moves.down(fraction, new Caret(7, 1)).equals(new Caret(7, 1)));

        String bothSlots = "(12÷5)÷(3÷4)";
        Caret position = new Caret(2, 0);
        for (int next : new int[] {5, 9, 11}) {
            position = moves.down(bothSlots, position);
            check("向下逐行保留列 → " + next, position.equals(new Caret(next, 0)));
        }
        for (int next : new int[] {9, 5, 2}) {
            position = moves.up(bothSlots, position);
            check("向上逐行保留列 → " + next, position.equals(new Caret(next, 0)));
        }
        step("外层分子中的分数后 → 进入外层分母", "(1÷2)÷3", 4, 1, true, 6, 0);
        step("左结合的旧算式也能逐层跳出", "1÷2÷3", 3, 0, true, 3, 1);
        step("夹在算式中间的分数左外侧 → 进分子", "9+1÷2", 2, 1, true, 2, 0);
        check("夹在算式中间的分数开头绘制在分子里", path("9+1÷2", 2, 0).contains("/num"));

        // 按主键盘顺序输入 x/y、1、▼、x/y、2、▼、3、▶、▶、+、1。
        EditResult entry = edits.insertFraction("", new Caret(0, 0));
        entry = edits.insert(entry.getText(), entry.getCursor(), "1");
        entry = edits.insertFraction(entry.getText(), moves.down(entry.getText(), new Caret(entry.getCursor(), 0)));
        check("空分母按分数键创建内层分数并落在内层分子", entry.equals(new EditResult("1÷(÷)", 3)));
        entry = edits.insert(entry.getText(), entry.getCursor(), "2");
        position = moves.down(entry.getText(), new Caret(entry.getCursor(), 0));
        entry = edits.insert(entry.getText(), position.getOffset(), "3");
        check("输入得到 1÷(2÷3)", entry.getText().equals(fraction));
        position = moves.right(fraction, moves.right(fraction, new Caret(entry.getCursor(), 0)));
        entry = edits.insert(fraction, position.getOffset(), "+1");
        check("退出后输入 +1，结果为 5/2", entry.getText().equals("1÷(2÷3)+1") &&
                Math.abs(CalcEngine.INSTANCE.evaluate(entry.getText(), AngleMode.DEG, 0, 0) - 2.5) < 1e-9);

        check("已填的外层分母作为新分数的分子", edits.insertFraction("1÷2", new Caret(3, 0))
                .equals(new EditResult("1÷(2÷)", 5)));
        check("空分子按分数键创建内层分数", edits.insertFraction("÷", new Caret(0, 0))
                .equals(new EditResult("(÷)÷", 1)));
        check("整个分数外按分数键，将原分数作为新分子", edits.insertFraction("1÷2", new Caret(3, 1))
                .equals(new EditResult("1÷2÷", 4)));
        check("整个分数左侧按分数键，将原分数作为新分母", edits.insertFraction("1÷2", new Caret(0, 1))
                .equals(new EditResult("÷(1÷2)", 0)));
        check("内层分数左侧按分数键，将原内层分数作为新分母", edits.insertFraction(fraction, new Caret(3, 1))
                .equals(new EditResult("1÷(÷(2÷3))", 3)));
    }

    static String path(String expression, int offset, int exit) {
        return NatCursorKt.natCursorPath(NatCursorKt.buildNatCursor(expression, offset, exit));
    }
}
