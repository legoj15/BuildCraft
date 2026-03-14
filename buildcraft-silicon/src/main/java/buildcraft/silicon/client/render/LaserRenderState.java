package buildcraft.silicon.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

/**
 * Render state snapshot for laser block entities.
 * Actual data is read from the level in RenderLaser.submit().
 */
public class LaserRenderState extends BlockEntityRenderState {
    public BlockPos blockPos;
}
