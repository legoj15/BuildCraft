package buildcraft.factory.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for distiller block entities.
 * Minimal state — actual fluid data is read from the level in RenderDistiller.submit().
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class DistillerRenderState extends BlockEntityRenderState {
//?} else {
/*public class DistillerRenderState {*/
//?}
}
