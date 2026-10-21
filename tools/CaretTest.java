import io.paimon.fx991.ui.Caret;
import io.paimon.fx991.ui.CaretModel;

/**
 * 光标结构层级回归（v2.1）：
 *   分数 / 指数 / 根号的内容末尾与结构边界共用同一偏移量，
 *   Caret(offset, exit) 要能把「分母末尾 → 结构右边 → 结构左边 → 分子开头」分开。
 */
public class CaretTest {
    static int pass = 0, fail = 0;

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

        step("分子开头 ← 绕到分数右外侧", F, 0, 0, false, 3, 1);
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

        System.out.println("RESULT CaretTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
