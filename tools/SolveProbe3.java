import io.paimon.fx991.engine.AngleMode;
import io.paimon.fx991.engine.EquationSolver;
import io.paimon.fx991.engine.SolveResult;

/** 疑点探针：负首项系数 / 近似回退的现状 */
public class SolveProbe3 {

    static void show(String expr) {
        System.out.print(expr + "  =>  ");
        try {
            SolveResult r = EquationSolver.INSTANCE.solve(expr, AngleMode.DEG);
            System.out.print("kind=" + r.getKind() + "  text=" + r.getText());
            if (!r.getNote().isEmpty()) System.out.print("  | note=" + r.getNote());
            System.out.println();
        } catch (Throwable t) {
            System.out.println("异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        show("-x^2=1");
        show("-x^2-1=0");
        show("2x^2+2x+2=0");
        show("2x^2-2x+2=0");
        show("x^2=2000000000000000000");
        show("x^2+1=2000000000000000000");
        show("x^2+x+1=0");
    }
}
