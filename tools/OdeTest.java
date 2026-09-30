import io.paimon.fx991.engine.OdeSolver;
import io.paimon.fx991.engine.OdeTable;
import io.paimon.fx991.engine.AngleMode;

/**
 * 微分方程求解回归：RK4 一阶 / 二阶 + 常系数线性解析解。
 */
public class OdeTest {

    static int pass = 0;
    static int fail = 0;

    static void near(String what, double got, double expect, double tol) {
        boolean good = Math.abs(got - expect) <= tol;
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " got=" + got + "  expect=" + expect);
    }

    static void eq(String what, String got, String expect) {
        boolean good = got.equals(expect);
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 42) + " => " + got + (good ? "" : " (expected " + expect + ")"));
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    public static void main(String[] args) {
        System.out.println("-- RK4 一阶 --");
        // dy/dx = y, y(0)=1  →  y(1) = e
        // h=0.1：RK4 全局误差 ~ h⁴，允许 1e-5
        OdeTable t1 = OdeSolver.INSTANCE.firstOrder("y", 0, 1, 1, 0.1, AngleMode.RAD);
        near("dy/dx=y, y(1) [h=0.1]", t1.getYs()[t1.getYs().length - 1], Math.E, 1e-5);
        eq("采样点数", String.valueOf(t1.getSteps() + 1), "11");
        // h=0.01：精度显著提高（验证 4 阶收敛）
        OdeTable t1b = OdeSolver.INSTANCE.firstOrder("y", 0, 1, 1, 0.01, AngleMode.RAD);
        near("dy/dx=y, y(1) [h=0.01]", t1b.getYs()[t1b.getYs().length - 1], Math.E, 1e-8);

        // dy/dx = x - y, y(0)=1  →  y(x) = x - 1 + 2e^{-x}, y(2)=1+2e^{-2}
        OdeTable t2 = OdeSolver.INSTANCE.firstOrder("x - y", 0, 1, 2, 0.05, AngleMode.RAD);
        near("dy/dx=x-y, y(2)", t2.getYs()[t2.getYs().length - 1], 1 + 2 * Math.exp(-2), 1e-6);

        System.out.println("-- 终点自动收尾到 xn（步长不够整除时） --");
        OdeTable tq = OdeSolver.INSTANCE.firstOrder("y", 0, 1, 1, 0.3, AngleMode.RAD);
        near("末点 x == xn", tq.getXs()[tq.getXs().length - 1], 1.0, 1e-12);
        eq("0.3 步长到 1.0 的步数", String.valueOf(tq.getSteps()), "4");

        System.out.println("-- RK4 二阶（内化方程组） --");
        // y'' = -y, y(0)=1, y'(0)=0  →  y = cos x ; y(pi/2) ≈ 0
        OdeTable t3 = OdeSolver.INSTANCE.secondOrder("-y", 0, 1, 0, Math.PI / 2, 0.001, AngleMode.RAD);
        near("y''=-y, y(pi/2)", t3.getYs()[t3.getYs().length - 1], 0.0, 1e-6);
        // 同时给出 y'
        near("y''=-y, y'(pi/2)", t3.getZs()[t3.getZs().length - 1], -1.0, 1e-6);

        System.out.println("-- 常系数线性解析解 --");
        // y'' + 3y' + 2y = 0, y(0)=1, y'(0)=0 → y = 2e^{-x} - e^{-2x}
        OdeSolver.Analytic a1 = OdeSolver.INSTANCE.analyticLinear(1, 3, 2, 0, 1, 0);
        near("y''+3y'+2y=0, y(1)", a1.value(1.0), 2 * Math.exp(-1) - Math.exp(-2), 1e-9);
        System.out.println("    " + a1.getKind());
        System.out.println("    " + a1.getFormula());

        // y'' + y = 0, y(0)=1, y'(0)=0 → cos x
        OdeSolver.Analytic a2 = OdeSolver.INSTANCE.analyticLinear(1, 0, 1, 0, 1, 0);
        near("y''+y=0, y(0.5)", a2.value(0.5), Math.cos(0.5), 1e-9);
        near("y''+y=0, y(3.0)", a2.value(3.0), Math.cos(3.0), 1e-9);

        // 重根：y'' - 2y' + y = 0, y(0)=1, y'(0)=0 → (1-x)e^x
        OdeSolver.Analytic a3 = OdeSolver.INSTANCE.analyticLinear(1, -2, 1, 0, 1, 0);
        near("y''-2y'+y=0, y(0.5)", a3.value(0.5), (1 - 0.5) * Math.exp(0.5), 1e-9);
        System.out.println("    " + a3.getKind());

        // 一阶线性：2y' + 4y = 0, y(0)=3 → 3e^{-2x}
        OdeSolver.Analytic a4 = OdeSolver.INSTANCE.analyticLinear(0, 2, 4, 0, 3, 0);
        near("2y'+4y=0, y(1)", a4.value(1.0), 3 * Math.exp(-2), 1e-9);

        System.out.println("-- 解析解采样成表 --");
        OdeTable t4 = OdeSolver.INSTANCE.sampleAnalytic(a2, 0, 1, 0.25);
        eq("解析表点数", String.valueOf(t4.getSteps() + 1), "5");
        near("解析表末点 y(1)", t4.getYs()[4], Math.cos(1.0), 1e-9);

        System.out.println("-- 错误处理 --");
        String e1 = "none";
        try {
            OdeSolver.INSTANCE.firstOrder("y", 0, 1, 1, 0, AngleMode.RAD);
        } catch (Throwable e) {
            e1 = e.getClass().getSimpleName();
        }
        eq("步长为 0", e1, "OdeError");

        String e2 = "none";
        try {
            OdeSolver.INSTANCE.firstOrder("y", 1, 1, 0, 0.1, AngleMode.RAD);
        } catch (Throwable e) {
            e2 = e.getClass().getSimpleName();
        }
        eq("xn <= x0", e2, "OdeError");

        String e3 = "none";
        try {
            OdeSolver.INSTANCE.firstOrder("foo(x)", 0, 1, 1, 0.1, AngleMode.RAD);
        } catch (Throwable e) {
            e3 = e.getClass().getSimpleName();
        }
        eq("非法表达式", e3, "CalcSyntaxError");

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
