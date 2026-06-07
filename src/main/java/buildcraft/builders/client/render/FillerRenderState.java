package buildcraft.builders.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for filler block entities.
 * Minimal state — actual data is read from the level in RenderFiller.submit().
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class FillerRenderState extends BlockEntityRenderState {
//?} else {
/*public class FillerRenderState {*/
//?}
}
