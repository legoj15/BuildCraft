/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

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
