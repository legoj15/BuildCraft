package buildcraft.factory.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for heat exchange block entities.
 * Minimal state — actual fluid data is read from the level in RenderHeatExchange.submit().
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class HeatExchangeRenderState extends BlockEntityRenderState {
//?} else {
/*public class HeatExchangeRenderState {*/
//?}
}
