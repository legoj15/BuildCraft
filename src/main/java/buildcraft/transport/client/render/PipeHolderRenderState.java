package buildcraft.transport.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

import buildcraft.transport.tile.TilePipeHolder;

/** Render state for pipe holder BER. Stores a direct reference to the tile
 *  entity and pre-resolved item render states so submit() doesn't need to
 *  do model resolution during the render phase. */
public class PipeHolderRenderState extends BlockEntityRenderState {
    /** Direct reference to the pipe holder tile, set during extractRenderState(). */
    public TilePipeHolder pipe;
    public float partialTick;

    /** Pre-resolved item render states for items travelling through the pipe.
     *  Each entry contains the ItemStackRenderState (model already resolved),
     *  position, direction, colour, and stack count for rendering. */
    public List<ItemRenderEntry> itemEntries = new ArrayList<>();

    /** A single item to be rendered inside a pipe, with pre-resolved model data. */
    public static class ItemRenderEntry {
        public final ItemStackRenderState renderState;
        public final double posX, posY, posZ;
        public final Direction direction;
        public final DyeColor colour;
        public final int stackCount;

        public ItemRenderEntry(ItemStackRenderState renderState,
                               double posX, double posY, double posZ,
                               Direction direction, DyeColor colour, int stackCount) {
            this.renderState = renderState;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.direction = direction;
            this.colour = colour;
            this.stackCount = stackCount;
        }
    }
}
