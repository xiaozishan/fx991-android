import io.paimon.fx991.ui.AsciiBlock;
import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;
import io.paimon.fx991.ui.Nat;
import io.paimon.fx991.ui.NatCursorKt;
import io.paimon.fx991.ui.NatModelKt;

/**
 * 批次 K3（A 可见光标 + B 二级界面自然输入）回归。
 *
 *  · A6：光标位置模型是纯逻辑 —— 移动 / 插入 / 删除后断言偏移量；
 *  · 光标落点：buildNatCursor 把 Nat.Cursor 插进布局树（分数分子/分母、根号、上标、括号组、空槽），
 *    用 ASCII 投影（▏ = U+258F）与结构路径（natCursorPath）双重断言；
 *  · B4：线性文本 ↔ 自然书写节点树 双向转换（natLinear 往返 / 不动点、预填值兼容）；
 *  · 模板键：模拟键盘面板 type(insert, back) 后断言文本与光标落点。
 */
public class CursorTest {

    static int pass = 0;
    static int fail = 0;

    static final String DIV = "÷";   // ÷
    static final String SQRT = "√";  // √
    static final String MINUS = "−"; // −
    static final String MUL = "×";   // ×
    static final String CARET = "▏"; // ▏

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    static String join(String[] ls) { return String.join("\n", ls); }

    /** 带光标的 ASCII 投影 */
    static String[] proj(String expr, int cur) {
        AsciiBlock b = NatModelKt.natAscii(NatCursorKt.buildNatCursor(expr, cur));
        return b.getLines().toArray(new String[0]);
    }

    static String path(String expr, int cur) {
        return NatCursorKt.natCursorPath(NatCursorKt.buildNatCursor(expr, cur));
    }

    static String lin(String expr) {
        return NatCursorKt.natLinearOf(expr);
    }

    static EditResult ins(String text, int cur, String t) {
        return CursorModel.INSTANCE.insert(text, cur, t);
    }

    /** 模拟键盘面板 type(insert, back)：插入后光标回退 back 格 */
    static int[] type(String[] box, String t, int back) {
        EditResult r = CursorModel.INSTANCE.insert(box[0], Integer.parseInt(box[1]), t);
        int nc = Math.max(0, Math.min(r.getText().length(), r.getCursor() - back));
        box[0] = r.getText();
        box[1] = String.valueOf(nc);
        return new int[]{ r.getText().length(), nc };
    }

    public static void main(String[] args) {
        System.out.println("== A6-1：光标移动（原子跳格 + 逐字符 + 边界） ==");

        // sin( 是一个整体
        check("moveRight 跳过 sin(", CursorModel.INSTANCE.moveRight("sin(x)", 0) == 4);
        check("moveRight 进入括号内", CursorModel.INSTANCE.moveRight("sin(x)", 4) == 5);
        check("moveRight 越过 x", CursorModel.INSTANCE.moveRight("sin(x)", 5) == 6);
        check("moveRight 到顶不动", CursorModel.INSTANCE.moveRight("sin(x)", 6) == 6);
        check("moveLeft 跳过 sin(", CursorModel.INSTANCE.moveLeft("sin(x)", 4) == 0);
        check("moveLeft 逐字符", CursorModel.INSTANCE.moveLeft("sin(x)", 6) == 5);
        check("moveLeft 到底不动", CursorModel.INSTANCE.moveLeft("sin(x)", 0) == 0);
        // 10^ 是一个整体
        check("moveRight 跳过 10^", CursorModel.INSTANCE.moveRight("10^3", 0) == 3);
        check("moveLeft 跳过 10^", CursorModel.INSTANCE.moveLeft("10^3", 3) == 0);
        // ×10^ 优先于 10^（长原子先匹配）
        check("moveRight 跳过 ×10^", CursorModel.INSTANCE.moveRight("2×10^5", 1) == 5);
        // ⁻¹ 两个字符当一个整体
        check("moveLeft 跳过 ⁻¹", CursorModel.INSTANCE.moveLeft("x⁻¹", 3) == 1);
        // 普通逐字符
        check("moveLeft 普通字符", CursorModel.INSTANCE.moveLeft("12+3", 1) == 0);
        check("moveRight 普通字符", CursorModel.INSTANCE.moveRight("12+3", 1) == 2);
        // 越界钳制
        check("clamp 上限", CursorModel.INSTANCE.clamp("12", 99) == 2);
        check("clamp 下限", CursorModel.INSTANCE.clamp("12", -3) == 0);
        check("moveLeft 越界安全", CursorModel.INSTANCE.moveLeft("12", 99) == 1);

        System.out.println();
        System.out.println("== A6-2：插入 / 退格 / 前删后光标跟随 ==");

        EditResult r = ins("12+3", 2, "×4");
        check("中间插入文本", r.getText().equals("12×4+3"));
        check("中间插入后光标跟随", r.getCursor() == 4);
        r = ins("12+3", 0, "(");
        check("开头插入", r.getText().equals("(12+3") && r.getCursor() == 1);
        r = ins("12+3", 4, "!");
        check("末尾插入", r.getText().equals("12+3!") && r.getCursor() == 5);
        r = ins("12+3", 99, "5");
        check("越界插入钳制", r.getText().equals("12+35") && r.getCursor() == 5);

        // a+sin(x)：光标在 ( 后面（偏移 6）→ 退格删掉整个 sin(
        r = CursorModel.INSTANCE.backspace("a+sin(x)", 6);
        check("退格删原子 sin(", r.getText().equals("a+x)") && r.getCursor() == 2);
        r = CursorModel.INSTANCE.backspace("12", 1);
        check("退格删单字符", r.getText().equals("2") && r.getCursor() == 0);
        r = CursorModel.INSTANCE.backspace("12", 0);
        check("退格到底不动", r.getText().equals("12") && r.getCursor() == 0);
        r = CursorModel.INSTANCE.backspace("1÷2", 2);
        check("退格删 ÷ 后光标在原分子后", r.getText().equals("12") && r.getCursor() == 1);
        // 前删（Delete 语义）
        r = CursorModel.INSTANCE.deleteForward("sin(x)", 0);
        check("前删删原子 sin(", r.getText().equals("x)") && r.getCursor() == 0);
        r = CursorModel.INSTANCE.deleteForward("12+3", 2);
        check("前删删单字符", r.getText().equals("123") && r.getCursor() == 2);
        r = CursorModel.INSTANCE.deleteForward("12", 2);
        check("前删到尾不动", r.getText().equals("12") && r.getCursor() == 2);

        System.out.println();
        System.out.println("== A-3：光标落点（ASCII 投影 + 结构路径） ==");

        String[] p = proj("", 0);
        check("空表达式也有光标", count(join(p), CARET) == 1);

        p = proj("1÷2", 0);
        check("分数：光标在分子开头", p[0].contains(CARET) && !p[2].contains(CARET));
        check("分数：路径含 /num", path("1÷2", 0).contains("/num"));
        p = proj("1÷2", 1);
        check("分数：光标在分子末尾", p[0].contains("1" + CARET) && count(join(p), CARET) == 1);
        p = proj("1÷2", 2);
        check("分数：光标走到分母开头", p[2].contains(CARET) && !p[0].contains(CARET));
        check("分数：路径含 /den", path("1÷2", 2).contains("/den"));
        p = proj("1÷2", 3);
        check("分数：光标在分母末尾", p[2].contains("2" + CARET));
        p = proj("12÷34", 1);
        check("分子 token 中间劈开", p[0].contains("1" + CARET + "2"));

        p = proj("√(x)", 2);
        check("根号内：光标在 x 前", join(p).contains("(" + CARET + "x") && count(join(p), CARET) == 1);
        check("根号：路径含 /sqrt", path("√(x)", 3).contains("/sqrt"));

        p = proj("x^2", 1);
        check("上标：光标在底数后（基线行）", p[p.length - 1].contains("x" + CARET));
        check("上标：路径含 /base", path("x^2", 1).contains("/base"));
        p = proj("x^2", 2);
        check("上标：光标进指数（抬升行）", p[0].contains(CARET) && p[0].contains("2"));
        check("上标：路径含 /exp", path("x^2", 2).contains("/exp"));
        p = proj("x^2", 3);
        check("上标：光标在指数末尾", p[0].contains("2" + CARET));

        p = proj("()", 1);
        check("空括号组：光标落在括号之间", join(p).contains("(" + CARET + ")"));
        check("嵌套根号：路径含 /sqrt", path("1÷(2+√(3))", 7).contains("/sqrt"));

        // 任意偏移都恰好一个光标（不丢、不重）
        String[] exprs = {
            "1÷2+3", "√(x^2+1)", "sin(x)+cos(2x)", "logb(2,8)×3",
            "2×10^5", "(1+2)÷(3−4)", "x⁻¹+1", "sin⁻¹(0.5)",
        };
        boolean allOne = true;
        for (String e : exprs) {
            for (int cur = 0; cur <= e.length(); cur++) {
                if (count(join(proj(e, cur)), CARET) != 1) { allOne = false; }
            }
        }
        check("8 个表达式 × 全部偏移：光标恰好一个", allOne);

        System.out.println();
        System.out.println("== B4：线性文本 ↔ 节点树 双向转换 ==");

        // 规范化往返 = 恒等（标准写法）
        String[][] canon = {
            {"1÷2", "1÷2"},
            {"x^2+1", "x^2+1"},
            {"√(3)", "√(3)"},
            {"√(x+1)", "√(x+1)"},
            {"sin(x)+cos(x)", "sin(x)+cos(x)"},
            {"logb(2,8)", "logb(2,8)"},
            {"2×10^5", "2×10^5"},
            {"(1+2)÷3", "(1+2)÷3"},
            {"1÷(2+3)", "1÷(2+3)"},
            {"x^(2×3)", "x^(2×3)"},
            {"1e-10", "1e−10"},   // 预填值：负号规范化为 −
            {"pi", "pi"},          // 预填值
            {"0", "0"},            // 预填值
        };
        for (String[] c : canon) {
            check("往返恒等 " + c[0], lin(c[0]).equals(c[1]));
        }
        // 不动点：lin(buildNat(lin(buildNat(x)))) == lin(buildNat(x))（嵌套分数不加无限括号）
        String[] tricky = { "1÷2÷3", "√(3)÷2", "x^2^3", "(1÷2)÷(3÷4)", "1÷2+3÷4" };
        for (String t : tricky) {
            String once = lin(t);
            check("不动点 " + t + " → " + once, lin(once).equals(once));
        }
        // 预填值可编辑：在 1e-10 中间插入
        r = ins("1e-10", 2, "5");
        check("预填值中间编辑", r.getText().equals("1e5-10") && r.getCursor() == 3);

        System.out.println();
        System.out.println("== B-模板键：插入后光标落在空位里 ==");

        String[] box = { "", "0" };
        type(box, "÷", 1);          // a/b
        check("a/b 插出 ÷", box[0].equals("÷"));
        check("a/b 光标留在分子空槽", path(box[0], Integer.parseInt(box[1])).contains("/num"));

        box = new String[]{ "", "0" };
        type(box, "²", 1);          // x²
        check("x² 插出 ²", box[0].equals("²"));
        check("x² 光标留在底数空槽", path(box[0], Integer.parseInt(box[1])).contains("/base"));

        box = new String[]{ "", "0" };
        type(box, "^", 1);          // xʸ
        check("xʸ 光标留在底数空槽", path(box[0], Integer.parseInt(box[1])).contains("/base"));

        box = new String[]{ "", "0" };
        type(box, "√(", 0);         // √
        check("√ 光标进根号", path(box[0], Integer.parseInt(box[1])).contains("/sqrt"));

        box = new String[]{ "", "0" };
        type(box, "10^", 0);        // 10ˣ
        check("10ˣ 光标进指数", path(box[0], Integer.parseInt(box[1])).contains("/exp"));

        box = new String[]{ "", "0" };
        type(box, "×10^", 0);       // Exp
        check("Exp 光标进指数", path(box[0], Integer.parseInt(box[1])).contains("/exp"));

        box = new String[]{ "", "0" };
        type(box, "⁻¹", 0);         // x⁻¹ → 1/□，光标在分母槽
        check("x⁻¹ 光标在分母槽", path(box[0], Integer.parseInt(box[1])).contains("/den"));

        box = new String[]{ "", "0" };
        type(box, "logb(", 0);      // logₓy
        check("logb( 光标进括号", Integer.parseInt(box[1]) == 5);

        box = new String[]{ "", "0" };
        type(box, "ncr(", 0);       // nCr
        check("ncr( 光标进括号", Integer.parseInt(box[1]) == 4);

        box = new String[]{ "", "0" };
        type(box, "sin⁻¹(", 0);     // sin⁻¹
        check("sin⁻¹( 光标进括号", Integer.parseInt(box[1]) == 6);

        // 模板里继续打字：x² 的底数槽里敲 3 → "3²"，光标在底数后
        box = new String[]{ "", "0" };
        type(box, "²", 1);
        type(box, "3", 0);
        check("x² 底数槽敲 3 → 3²", box[0].equals("3²") && Integer.parseInt(box[1]) == 1);
        check("3² 投影是底数+上标", join(proj(box[0], Integer.parseInt(box[1]))).contains("3"));

        // a/b 模板：分子里敲 1、方向键右移、分母敲 2 → 1÷2
        box = new String[]{ "", "0" };
        type(box, "÷", 1);
        type(box, "1", 0);
        box[1] = String.valueOf(CursorModel.INSTANCE.moveRight(box[0], Integer.parseInt(box[1])));
        type(box, "2", 0);
        check("a/b 模板敲出 1÷2", box[0].equals("1÷2"));
        check("1÷2 光标在分母后", Integer.parseInt(box[1]) == 3);

        System.out.println();
        System.out.println("== K3-symbolic 修复：方向键垂直移动（▲▼） ==");

        // P1：▼ 从指数「出」到上标块之后的基线位，不再落进 "^" 之前
        check("x^2 指数末尾 ▼ → 留在块尾", CursorModel.INSTANCE.moveDown("x^2", 3) == 3);
        check("x^2 指数中间 ▼ → 出到块尾", CursorModel.INSTANCE.moveDown("x^2", 2) == 3);
        check("x² 指数末尾 ▼ → 留在块尾", CursorModel.INSTANCE.moveDown("x²", 2) == 2);
        // 复现原 bug 的完整操作流：x^2 ▼ +3 必须得到 x^2+3（旧逻辑给 x+3^2）
        r = ins("x^2", CursorModel.INSTANCE.moveDown("x^2", 3), "+3");
        check("x^2 ▼ 后打 +3 → x^2+3", r.getText().equals("x^2+3"));
        r = ins("x²", CursorModel.INSTANCE.moveDown("x²", 2), "×5");
        check("x² ▼ 后打 ×5 → x²×5", r.getText().equals("x²×5"));
        // ▲ 从底数进指数仍保留
        check("x^2 底数 ▲ → 进指数", CursorModel.INSTANCE.moveUp("x^2", 1) == 3);

        // P2：结构边缘位不再冻死 —— 按线性式走首/尾
        check("√(3) 行首 ▼ → 到尾", CursorModel.INSTANCE.moveDown("√(3)", 0) == 4);
        check("√(3) 行首 ▲ → 到首", CursorModel.INSTANCE.moveUp("√(3)", 0) == 0);
        check("int(x,0,1) 末尾 ▲ → 到首", CursorModel.INSTANCE.moveUp("int(x,0,1)", 10) == 0);
        check("int(x,0,1) 末尾 ▼ → 到尾", CursorModel.INSTANCE.moveDown("int(x,0,1)", 10) == 10);
        check("int(x,0,1) 前缀 ▼ → 到尾", CursorModel.INSTANCE.moveDown("int(x,0,1)", 1) == 10);
        // 槽位里该方向没去处仍原地（不回归）
        check("1÷2 分母末尾 ▼ → 原地", CursorModel.INSTANCE.moveDown("1÷2", 3) == 3);
        check("1÷2 分子开头 ▲ → 原地", CursorModel.INSTANCE.moveUp("1÷2", 0) == 0);

        // P3：光标在容器前位置 → 画到容器前面（√ 符号位 / int( 前缀）
        p = proj("√(3)", 0);
        check("√(3) 行首光标画在 √ 前", join(p).contains(CARET + "√"));
        p = proj("int(x,0,1)", 0);
        check("int( 前缀光标画在 ∫ 前", join(p).contains(CARET + "∫"));
        // 原来的落点行为不回归
        check("√(3) 根号内光标仍进槽", path("√(3)", 2).contains("/sqrt"));
        check("int 三槽光标仍进槽", path("int(x,0,1)", 4).contains("/body")
                && path("int(x,0,1)", 6).contains("/lo") && path("int(x,0,1)", 8).contains("/hi"));

        System.out.println();
        System.out.println("== K3-symbolic 修复：主行分数键 insertFraction ==");

        // P4：分子空槽 → 光标留分子；分子有内容 → 进分母
        r = CursorModel.INSTANCE.insertFraction("", 0);
        check("空行分数键 → ÷", r.getText().equals("÷"));
        check("空行分数键光标在分子", path(r.getText(), r.getCursor()).contains("/num"));
        r = CursorModel.INSTANCE.insertFraction("3+", 2);
        // 运算符后光标位置可能渲染在 "+" 之后（边界归属），但打字必须落进分子
        EditResult r2 = CursorModel.INSTANCE.insert(r.getText(), r.getCursor(), "1");
        check("运算符后分数键：打字落进分子", r.getText().equals("3+÷") && r2.getText().equals("3+1÷"));
        r = CursorModel.INSTANCE.insertFraction("12", 2);
        check("数字后分数键光标在分母", r.getText().equals("12÷") && path(r.getText(), r.getCursor()).contains("/den"));
        r = CursorModel.INSTANCE.insertFraction("(1+2)", 5);
        check("闭括号后分数键光标在分母", r.getText().equals("(1+2)÷") && path(r.getText(), r.getCursor()).contains("/den"));

        System.out.println();
        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
