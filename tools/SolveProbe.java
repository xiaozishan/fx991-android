import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.InlineFuncs;
import io.paimon.fx991.engine.SolveResult;
import io.paimon.fx991.engine.SolveKind;
import io.paimon.fx991.ui.NatModelKt;

/** K3-symbolic 现状探针：solve(...) 带等号的真实行为 */
public class SolveProbe {

    static void show(String expr) {
        System.out.println("=== " + expr + " ===");
        // 1) 投影（用户在 LCD 上看到什么）
        try {
            String proj = NatModelKt.natAsciiText(expr);
            System.out.println("  投影: " + proj.replace("\n", " | "));
        } catch (Throwable t) {
            System.out.println("  投影异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        // 2) unwrapSolve 外壳拆解
        String inner = InlineFuncs.unwrapSolve(expr);
        System.out.println("  unwrapSolve => " + inner);
        // 3) 模拟按 = 的求解（ViewModel.evaluateNow → runEquation）
        String target = inner != null ? inner : expr;
        try {
            if (EquationSolver.looksLikeEquation(target) && !EquationSolver.unknowns(target).isEmpty()) {
                SolveResult r = EquationSolver.solve(target, AngleMode.DEG);
                System.out.println("  kind=" + r.getKind() + "  text=" + r.getText());
                if (!r.getNote().isEmpty()) System.out.println("  note=" + r.getNote());
                r.getItems().forEach(it ->
                    System.out.println("    item: " + it.getLabel() + "  [insert=" + it.getInsert() + "]"));
            } else {
                System.out.println("  (不当方程：looksLike=" + EquationSolver.looksLikeEquation(target)
                        + " unknowns=" + EquationSolver.unknowns(target) + ")");
            }
        } catch (Throwable t) {
            System.out.println("  求解异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        System.out.println();
    }

    public static void main(String[] args) {
        show("solve(2x+3=7)");
        show("solve(x+1=3)");
        show("solve(x^2=4)");
        show("solve(x^2=2)");
        show("solve(x^2−3x+2=0)");
        show("solve(2x+3)");
        // 对照：主行裸方程（不走 solve 外壳）
        show("2x+3=7");
        show("x^2=4");
        show("x^2=2");
    }
}
