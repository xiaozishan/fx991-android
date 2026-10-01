import io.paimon.fx991.ui.NatModelKt;

/** ± 在自然书写渲染里的去向 */
public class PmProbe {
    static void show(String expr) {
        System.out.println("=== [" + expr + "]");
        try {
            String t = NatModelKt.natAsciiText(expr);
            System.out.println(t.replace("\n", " | "));
        } catch (Throwable t) {
            System.out.println("THROW: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        show("x = ±i");
        show("x = ±√2");
        show("x = -1 ± 2i");
        show("x = (-1 ± √3i)/2");
        show("x = 1, (-1 ± √3i)/2");
        show("x = 2");
    }
}
