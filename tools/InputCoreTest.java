import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import io.paimon.fx991.ui.InputCore;

/**
 * 输入体验重构 · 第一批：结构感知编辑核心回归。
 * 覆盖：空组整体删除 / 悬挂结构清理 / 原子删除 / 前删镜像 / 光标落点。
 */
public class InputCoreTest {
    static int pass = 0, fail = 0;

    static void bs(String label, String text, int pos, String want, int wantCur) {
        EditResult r = InputCore.INSTANCE.backspace(text, pos);
        boolean ok = r.getText().equals(want) && r.getCursor() == wantCur;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label + "  ⟶ \"" + r.getText() + "\" @" + r.getCursor()
                + (ok ? "" : "   (want \"" + want + "\" @" + wantCur + ")"));
    }

    static void df(String label, String text, int pos, String want, int wantCur) {
        EditResult r = InputCore.INSTANCE.deleteForward(text, pos);
        boolean ok = r.getText().equals(want) && r.getCursor() == wantCur;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label + "  ⟶ \"" + r.getText() + "\" @" + r.getCursor()
                + (ok ? "" : "   (want \"" + want + "\" @" + wantCur + ")"));
    }

    static void truth(String label, boolean got) {
        if (got) { pass++; System.out.println("  PASS  " + label); }
        else { fail++; System.out.println("  FAIL  " + label); }
    }

    public static void main(String[] a) {
        String DIV = "÷", SQRT = "√", MUL = "×";

        System.out.println("[空括号组：整体删除]");
        bs("sin() 内退格", "sin()", 4, "", 0);
        bs("√() 内退格", SQRT + "()", 2, "", 0);
        bs("嵌套 sin(cos()) 内层删", "sin(cos())", 8, "sin()", 4);
        bs("空组后面还有运算", "2+sin()", 7, "2+", 2);

        System.out.println("[悬挂结构清理]");
        bs("3÷ 末尾删 ÷", "3" + DIV, 2, "3", 1);
        bs("3÷ 删 3", "3" + DIV, 1, "", 0);
        bs("2+3÷4 删 4", "2+3" + DIV + "4", 5, "2+3", 3);
        bs("2^ 末尾删 ^", "2^", 2, "2", 1);
        bs("裸 √ 删掉", SQRT, 1, "", 0);
        bs("2× 末尾删 ×（保留运算符待输入）", "2" + MUL, 2, "2", 1);

        System.out.println("[原子 & 常规]");
        bs("sin( 整体删", "sin(", 4, "", 0);
        bs("数字退格", "1234", 4, "123", 3);
        bs("光标居中退格", "1234", 2, "134", 1);
        bs("开头退格无操作", "123", 0, "123", 0);

        System.out.println("[前删镜像]");
        df("sin() 内前删", "sin()", 3, "", 0);
        df("3÷ 前删 ÷", "3" + DIV, 1, "3", 1);
        df("数字前删", "1234", 1, "134", 1);

        System.out.println("[结构识别]");
        truth("sin(3) 光标在括号内", InputCore.INSTANCE.inSlot("sin(3)", 4));
        truth("sin(3) 光标在括号外", !InputCore.INSTANCE.inSlot("sin(3)", 6));
        truth("空组定位 sin()", InputCore.INSTANCE.emptyGroupAt("sin()", 4) != null);
        truth("非空组不算空", InputCore.INSTANCE.emptyGroupAt("sin(1)", 4) == null);

        System.out.println("[兼容旧路径]");
        EditResult r = CursorModel.INSTANCE.insert("12", 1, "3");
        truth("插入 12→132 @2", r.getText().equals("132") && r.getCursor() == 2);

        System.out.println("RESULT InputCoreTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
