import net.minecraft.world.level.block.state.BlockBehaviour;
import java.lang.reflect.Method;
public class CheckMethods { 
    public static void main(String[] args) { 
        for(Method m : BlockBehaviour.class.getDeclaredMethods()) { 
            if (m.getParameterCount() == 5 && m.getParameterTypes()[4] == boolean.class) {
                System.out.println("METHOD: " + m.getName()); 
            }
        } 
    } 
}
