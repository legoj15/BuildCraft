package buildcraft.factory.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}
import net.minecraft.core.BlockPos;

/**
 * Render state snapshot for tank block entities.
 * Minimal state — actual fluid data is read from the level in RenderTank.submit().
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class TankRenderState extends BlockEntityRenderState {
//?} else {
/*public class TankRenderState {*/
//?}
    public BlockPos blockPos;
}
