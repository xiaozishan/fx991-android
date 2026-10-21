import io.paimon.fx991.engine.Rational;
import io.paimon.fx991.engine.Surd;
import java.math.BigInteger;

public class SurdTest {
    static int pass = 0, fail = 0;

    static void eq(String label, String got, String want) {
        if (got.equals(want)) { pass++; System.out.println("  PASS " + label + " = " + got); }
        else { fail++; System.out.println("  FAIL " + label + " = " + got + " (want " + want + ")"); }
    }

    public static void main(String[] a) {
        Surd s = Surd.INSTANCE;

        eq("√8", s.format(s.sqrt(8L)), "2√2");
        eq("√12", s.format(s.sqrt(12L)), "2√3");
        eq("√18", s.format(s.sqrt(18L)), "3√2");
        eq("√50", s.format(s.sqrt(50L)), "5√2");
        eq("√3", s.format(s.sqrt(3L)), "√3");
        eq("√16", s.format(s.sqrt(16L)), "4");
        eq("√0", s.format(s.sqrt(0L)), "0");

        Surd.Radical r2 = s.sqrt(2L);
        Surd.Radical r8 = s.sqrt(8L);
        Surd.Radical sum = s.add(r2, r8);
        eq("√2+√8", s.format(sum), "3√2");
        eq("√8-√2", s.format(s.sub(r8, r2)), "√2");
        eq("√2·√8", s.format(s.mul(r2, r8)), "4");
        eq("√2·√3", s.format(s.mul(r2, s.sqrt(3L))), "√6");
        eq("1/√2", s.format(s.div(s.sqrt(1L), r2)), "√2/2");
        eq("√8/√2", s.format(s.div(r8, r2)), "2");

        // 分数被开方：√(9/4) = 3/2
        Rational nineQuarters = Rational.Companion.of(BigInteger.valueOf(9), BigInteger.valueOf(4));
        Surd.Radical r = s.sqrt(nineQuarters);
        eq("√(9/4)", s.format(r), "3/2");

        // 数值校验
        double d = r2.toDouble();
        if (Math.abs(d - Math.sqrt(2.0)) < 1e-12) { pass++; System.out.println("  PASS √2 数值"); }
        else { fail++; System.out.println("  FAIL √2 数值 " + d); }

        System.out.println("RESULT SurdTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
