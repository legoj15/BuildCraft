import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.client.multiplayer.ClientLevel;
import java.lang.reflect.Method;

public class TestMethods {
    public static void main(String[] args) {
        System.out.println("Methods in Ingredient:");
        for (Method m : Ingredient.class.getMethods()) {
            System.out.println(m.getName() + " -> " + m.getReturnType().getName());
        }
        
        System.out.println("\nMethods in ClientLevel:");
        for (Method m : ClientLevel.class.getMethods()) {
            if (m.getName().toLowerCase().contains("recipe")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getName());
            }
        }
    }
}
