package buildcraft.transport.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import buildcraft.transport.tile.TilePipeHolder;

/** Render state for pipe holder BER. Stores a direct reference to the tile
 *  entity so submit() doesn't need to look it up from world coordinates. */
public class PipeHolderRenderState extends BlockEntityRenderState {
    /** Direct reference to the pipe holder tile, set during extractRenderState(). */
    public TilePipeHolder pipe;
}
