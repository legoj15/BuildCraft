package buildcraft.core;
import net.minecraft.nbt.CompoundTag;

public class TestData {
    public static void test(CompoundTag nbt) {
        java.lang.reflect.Method[] methods = CompoundTag.class.getMethods();
        for (java.lang.reflect.Method m : methods) {
            System.out.println(m.getName() + " -> " + m.getReturnType().getSimpleName());
            for (Class<?> p : m.getParameterTypes()) {
                System.out.println("  " + p.getSimpleName());
            }
        }
    }
}
