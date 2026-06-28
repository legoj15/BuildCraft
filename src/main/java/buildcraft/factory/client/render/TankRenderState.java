package buildcraft.factory.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for tank block entities. Minimal — fluid data and the tile position are read from
 * the level in {@code RenderTank.submit()} via the inherited {@code blockPos}.
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class TankRenderState extends BlockEntityRenderState {
//?} else {
/*public class TankRenderState {*/
//?}
}
