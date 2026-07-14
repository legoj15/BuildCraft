package buildcraft.lib.misc;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
public class RenderUtil {
    public static class AutoTessellator implements AutoCloseable {
        public Object tessellator;
        @Override public void close() {}
    }
    public static AutoTessellator getThreadLocalUnusedTessellator() { return new AutoTessellator(); }
    public static void drawAABB(AABB box, VertexConsumer bb) {}
}
