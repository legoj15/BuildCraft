import java.lang.reflect.*;
public class Dump {
  public static void main(String[] args) throws Exception {
    for (Method m : Class.forName("net.neoforged.neoforge.transfer.transaction.Transaction$Lifecycle").getMethods()) {
      System.out.println(m);
    }
  }
}
