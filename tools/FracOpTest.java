import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.CalcEngine;

/**
 * 批次 K6：分数槽内运算符归属回归。
 *
 *  · 分数分母 / 分子槽里直接打 × ÷ 时，运算符要留在槽内（自动补一层括号保语义），
 *    不能掉到分数外面；+ − 保持原语义（落到分数外）。
 *  · 已经是整组括号的槽（6÷(5×6)）再打运算符 → 进括号内，不重复套括号。
 *  · 槽首 / 空槽 → 不包装，按普通输入走。
 */
public class FracOpTest {

    static int pass = 0;
    static int fail = 0;

    static final String DIV = "÷";
    static final String MUL = "×";
    static final String PLUS = "+";

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

    /** 从一次编辑结果继续敲键 */
    static EditResult type2(EditResult r, String keys) {
        return type(r.getText(), r.getCursor(), keys);
    }

    /** 模拟逐键输入：每按一次字符/运算符都走 insertOperator 路径 */
    static EditResult type(String expr, int cur, String keys) {        for (int i = 0; i < keys.length(); i++) {
            String k = String.valueOf(keys.charAt(i));
            EditResult r = (k.equals(MUL) || k.equals(DIV))
                    ? CursorModel.INSTANCE.insertOperator(expr, cur, k)
                    : CursorModel.INSTANCE.insert(expr, cur, k);
            expr = r.getText();
            cur = r.getCursor();
        }
        return new EditResult(expr, cur);
    }

    public static void main(String[] args) {
        // 1) 分母里打 5×6：不打括号，乘号留在分数底下
        eq("6÷5 再按 × 包住分母",
                CursorModel.INSTANCE.insertOperator("6" + DIV + "5", 3, MUL).getText(),
                "6" + DIV + "(5" + MUL + ")");
        eq("6÷5×6 完整输入",
                type("6" + DIV + "5", 3, MUL + "6").getText(),
                "6" + DIV + "(5" + MUL + "6)");

        // 2) 分母中间插入（光标在 1 和 2 之间）
        eq("光标在分母中间插 × 也包住整个槽",
                CursorModel.INSTANCE.insertOperator("6" + DIV + "12", 3, MUL).getText(),
                "6" + DIV + "(1" + MUL + "2)");

        // 3) 分子槽同样处理
        eq("分子里按 × 包住分子",
                CursorModel.INSTANCE.insertOperator("12" + DIV + "5", 2, MUL).getText(),
                "(12" + MUL + ")" + DIV + "5");

        // 4) + 不参与：仍落分数外
        eq("分母后按 + 仍在分数外",
                CursorModel.INSTANCE.insertOperator("6" + DIV + "5", 3, PLUS).getText(),
                "6" + DIV + "5" + PLUS);

        // 5) 已有整组括号：进括号内，不重复套
        eq("括号槽再按 × 进括号内",
                CursorModel.INSTANCE.insertOperator("6" + DIV + "(5" + MUL + "6)", 9, MUL).getText(),
                "6" + DIV + "(5" + MUL + "6" + MUL + ")");

        // 6) 光标在整组括号末尾（槽尾）也进括号内
        eq("槽尾紧贴右括号时进括号内",
                CursorModel.INSTANCE.insertOperator("6" + DIV + "(5" + MUL + "6)", 10, MUL).getText(),
                "6" + DIV + "(5" + MUL + "6" + MUL + ")");

        // 7) 空槽不包装
        eq("空分母直接输入不包装",
                CursorModel.INSTANCE.insertOperator("6" + DIV, 2, MUL).getText(),
                "6" + DIV + MUL);

        // 8) 复合表达式里的分数同样生效
        eq("2+6÷5 末尾按 ×",
                CursorModel.INSTANCE.insertOperator("2+" + "6" + DIV + "5", 5, MUL).getText(),
                "2+6" + DIV + "(5" + MUL + ")");

        // 9) 光标跟随：包装后光标落在运算符之后
        EditResult r = CursorModel.INSTANCE.insertOperator("6" + DIV + "5", 3, MUL);
        check("包装后光标在运算符之后 => " + r.getCursor(), r.getCursor() == 5);
        EditResult r2 = type("6" + DIV + "5", 3, MUL + "6");
        check("继续输入 6 后光标在分母末尾 => " + r2.getCursor(), r2.getCursor() == r2.getText().length() - 1);

        // 10) 嵌套分数：只包最内层
        eq("嵌套分数只包最内层分母",
                CursorModel.INSTANCE.insertOperator("1" + DIV + "(2" + DIV + "3)", 6, MUL).getText(),
                "1" + DIV + "(2" + DIV + "(3" + MUL + "))");

        // 11) 指数里按分数键：分数要留在指数里，不能掉到基线下
        EditResult e1 = CursorModel.INSTANCE.insertFraction("x^2", 3);
        eq("x^2 末尾按分数键 → x^(2÷)", e1.getText(), "x^(2" + DIV + ")");
        check("光标落在分母位置 => " + e1.getCursor(), e1.getCursor() == 5);
        eq("继续输 3 → 指数内分数",
                type2(e1, "3").getText(), "x^(2" + DIV + "3)");
        near("8^(2÷3) = 4", CalcEngine.INSTANCE.evaluate("8^(2" + DIV + "3)", AngleMode.DEG, 0.0, 0.0), 4.0, 1e-9);

        // 12) 指数已有括号：进括号内，不重复套
        eq("x^(2÷3) 再按分数键进括号内",
                CursorModel.INSTANCE.insertFraction("x^(2" + DIV + "3)", 7).getText(),
                "x^(2" + DIV + "3" + DIV + ")");

        // 13) 普通位置不受影响
        eq("非指数位置仍插 ÷", CursorModel.INSTANCE.insertFraction("12", 2).getText(), "12" + DIV);

        // 14) 空指数：x^ 后直接按分数键（用户最常走的路）
        EditResult e2 = CursorModel.INSTANCE.insertFraction("x^", 2);
        eq("x^ 后按分数键 → x^(÷)", e2.getText(), "x^(" + DIV + ")");
        check("光标留在分子 => " + e2.getCursor(), e2.getCursor() == 3);
        eq("输 3 → x^(3÷)", type2(e2, "3").getText(), "x^(3" + DIV + ")");
        EditResult r3 = type2(e2, "3");
        EditResult mv = new EditResult(r3.getText(), CursorModel.INSTANCE.moveRight(r3.getText(), r3.getCursor()));
        eq("▶ 到分母后输 2 → x^(3÷2)", type2(mv, "2").getText(), "x^(3" + DIV + "2)");
        near("8^(3÷2) = 22.6274", CalcEngine.INSTANCE.evaluate("8^(3" + DIV + "2)", AngleMode.DEG, 0.0, 0.0), 22.62741699, 1e-6);

        // 15) 指数末尾退出到正常行内
        EditResult x1 = CursorModel.INSTANCE.exitSupIfAtEnd("x^2", 3);
        check("x^2 末尾按 ▶ 会补括号退出", x1 != null && x1.getText().equals("x^(2)") && x1.getCursor() == 5);
        check("非末尾不触发退出", CursorModel.INSTANCE.exitSupIfAtEnd("x^2", 2) == null);
        check("已是括号指数不再处理", CursorModel.INSTANCE.exitSupIfAtEnd("x^(2)", 5) == null);
        check("空指数不处理", CursorModel.INSTANCE.exitSupIfAtEnd("x^", 2) == null);
        near("退出后接 +1 在行内", CalcEngine.INSTANCE.evaluate("4^(1)+1", AngleMode.DEG, 0.0, 0.0), 5.0, 1e-9);

        System.out.println("RESULT FracOpTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
