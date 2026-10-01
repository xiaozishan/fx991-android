import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.InlineFuncs;
import io.paimon.fx991.engine.SolveResult;

/** K3-symbolic 探针 2：复数解 / 三次 / 超越 的现状 */
public class SolveProbe2 {

    static void show(String expr) {
        String inner = InlineFuncs.unwrapSolve(expr);
        String target = inner != null ? inner : expr;
        System.out.print(expr + "  =>  ");
        try {
            if (EquationSolver.looksLikeEquation(target) && !EquationSolver.unknowns(target).isEmpty()) {
                SolveResult r = EquationSolver.solve(target, AngleMode.DEG);
                System.out.print("kind=" + r.getKind() + "  text=" + r.getText());
                if (!r.getNote().isEmpty()) System.out.print("  | note=" + r.getNote());
                System.out.println();
                r.getItems().forEach(it ->
                    System.out.println("    item: " + it.getLabel() + "  [insert=" + it.getInsert() + "]"));
            } else {
                System.out.println("(不当方程)");
            }
        } catch (Throwable t) {
            System.out.println("异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        show("solve(x^2+1=0)");
        show("solve(x^2=-1)");
        show("solve(x^2+2x+5=0)");
        show("solve(x^3=1)");
        show("solve(x^3-1=0)");
        show("solve(3x+1=5)");
        show("solve(sin(x)=0.5)");
        show("solve(x^4=1)");
        show("solve(x^2+x+1=0)");
    }
}
