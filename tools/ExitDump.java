import io.paimon.fx991.ui.*;
public class ExitDump {
  static void p(String s, int o, int e){ System.out.println("=== "+s+" @"+o+" exit="+e+" ==="); System.out.println(TreeDump.dump(NatCursorKt.buildNatCursor(s,o,e),"")); }
  public static void main(String[] a){
    p("(6÷5)",4,0); p("(6÷5)",4,1); p("(6÷5)",5,0); p("6÷5×3",3,1);
  }
}
