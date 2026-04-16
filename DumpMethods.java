import java.lang.reflect.Method;
public class DumpMethods {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.neoforged.neoforge.transfer.transaction.TransactionContext");
        for (Method m : clazz.getMethods()) {
            System.out.println(m);
        }
    }
}
