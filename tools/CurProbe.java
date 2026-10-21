import io.paimon.fx991.ui.CursorModel;

public class CurProbe {
    static void p(String e, int c) {
        System.out.println("expr=[" + e + "] pos=" + c
            + "  left=" + CursorModel.INSTANCE.moveLeft(e, c)
            + "  right=" + CursorModel.INSTANCE.moveRight(e, c)
            + "  up=" + CursorModel.INSTANCE.moveUp(e, c)
            + "  down=" + CursorModel.INSTANCE.moveDown(e, c));
    }
    public static void main(String[] a) {
        p("x^2", 3); p("x^2", 2); p("x^2", 1); p("x^2", 0);
        p("x^(2)", 5); p("x^(2)", 4); p("x^(2)", 3);
        p("2^x+1", 3);
    }
}
