package buildcraft.factory.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

/**
 * Render state snapshot for tank block entities.
 * Minimal state — actual fluid data is read from the level in RenderTank.submit().
 */
public class TankRenderState extends BlockEntityRenderState {
    public BlockPos blockPos;
}
