import java.lang.reflect.*;
public class Dump {
  public static void main(String[] args) throws Exception {
    Method[] methods = Class.forName("net.neoforged.neoforge.transfer.access.ItemAccess").getMethods();
    for(Method m: methods) {
      if(m.getName().equals("forInfiniteMaterials")) {
        System.out.println("forInfiniteMaterials exists!");
      }
    }
  }
}
