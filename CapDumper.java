import java.lang.reflect.Field;
public class CapDumper {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.neoforged.neoforge.capabilities.Capabilities");
        for (Class<?> inner : clazz.getDeclaredClasses()) {
            System.out.println("Inner Class: " + inner.getName());
            for (Field f : inner.getDeclaredFields()) {
                System.out.println("  Field: " + f.getName());
            }
        }
    }
}
