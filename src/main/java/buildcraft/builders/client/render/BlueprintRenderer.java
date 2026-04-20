package buildcraft.builders.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.lib.misc.VecUtil;

public class BlueprintRenderer {

    public static void renderSnapshot(GuiGraphicsExtractor graphics, Snapshot snapshot, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        if (!(snapshot instanceof Blueprint blueprint)) {
            return;
        }

        double scale = Math.min(
                (viewportWidth - 10) / (double) Math.max(1, snapshot.size.getX()),
                (viewportHeight - 10) / (double) Math.max(1, snapshot.size.getY())
        );
        scale = Math.min(scale, (viewportWidth - 10) / (double) Math.max(1, snapshot.size.getZ()));
        // Make items fit appropriately (fakeItem renders 16x16)
        scale *= 12.0;

        double yaw = (System.currentTimeMillis() % 3600) / 3600.0 * 2 * Math.PI;
        double pitch = 20 * Math.PI / 180.0;
        
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        
        double cx = snapshot.size.getX() / 2.0;
        double cy = snapshot.size.getY() / 2.0;
        double cz = snapshot.size.getZ() / 2.0;
        
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        
        // Collect all blocks to render
        java.util.List<RenderBlock> blocks = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        
        for (int z = 0; z < snapshot.size.getZ(); z++) {
            for (int y = 0; y < snapshot.size.getY(); y++) {
                for (int x = 0; x < snapshot.size.getX(); x++) {
                    pos.set(x, y, z);
                    int index = blueprint.data[Snapshot.posToIndex(snapshot.size, pos)];
                    if (index >= 0 && index < blueprint.palette.size()) {
                        ISchematicBlock schBlock = blueprint.palette.get(index);
                        if (schBlock != null && !schBlock.isAir()) {
                            double px = (x + 0.5) - cx;
                            double py = -(y + 0.5) + cy; // Y is flipped in screen space
                            double pz = (z + 0.5) - cz;
                            
                            double rx = px * cosYaw - pz * sinYaw;
                            double rz = px * sinYaw + pz * cosYaw;
                            
                            double ry = py * cosPitch - rz * sinPitch;
                            double rz2 = py * sinPitch + rz * cosPitch; // Depth
                            
                            // To match standard isometric look
                            int screenX = viewportX + viewportWidth / 2 + (int)(rx * scale);
                            int screenY = viewportY + viewportHeight / 2 + (int)(ry * scale);
                            
                            // Visual offset tweaks
                            screenY += 10;
                            
                            blocks.add(new RenderBlock(schBlock, screenX, screenY, rz2));
                        }
                    }
                }
            }
        }
        
        // Sort by depth (further away renders first)
        blocks.sort((b1, b2) -> Double.compare(b1.depth, b2.depth));
        
        for (RenderBlock block : blocks) {
            for (ItemStack stack : block.schBlock.computeRequiredItems()) {
                if (!stack.isEmpty()) {
                    graphics.fakeItem(stack, block.x - 8, block.y - 8);
                }
            }
        }
        
        graphics.disableScissor();
    }
    
    private static class RenderBlock {
        final ISchematicBlock schBlock;
        final int x, y;
        final double depth;
        RenderBlock(ISchematicBlock schBlock, int x, int y, double depth) {
            this.schBlock = schBlock; this.x = x; this.y = y; this.depth = depth;
        }
    }
}
