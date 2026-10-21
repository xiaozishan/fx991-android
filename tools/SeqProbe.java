import io.paimon.fx991.ui.CursorModel;
import io.paimon.fx991.ui.EditResult;

public class SeqProbe {
    static void step(String label, EditResult r) {
        System.out.println(String.format("%-16s text=[%s] cursor=%d", label, r.getText(), r.getCursor()));
    }
    static EditResult ins(String e, int c, String s) { return CursorModel.INSTANCE.insert(e, c, s); }
    static EditResult right(String e, int c) {
        EditResult x = CursorModel.INSTANCE.exitSupIfAtEnd(e, c);
        if (x != null) return x;
        return new EditResult(e, CursorModel.INSTANCE.moveRight(e, c));
    }
    public static void main(String[] a) {
        EditResult r = ins("", 0, "e");
        step("e", r);
        r = ins(r.getText(), r.getCursor(), "^");
        step("^", r);
        r = ins(r.getText(), r.getCursor(), "1");
        step("1", r);
        r = ins(r.getText(), r.getCursor(), "1");
        step("1", r);
        r = right(r.getText(), r.getCursor());
        step("RIGHT", r);
        r = ins(r.getText(), r.getCursor(), "+1");
        step("+1", r);
    }
}
