package buildcraft.api.transport.pipe;

import javax.annotation.Nullable;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

public interface IPipe {
    IPipeHolder getHolder();

    PipeDefinition getDefinition();

    PipeBehaviour getBehaviour();

    PipeFlow getFlow();

    DyeColor getColour();

    void setColour(DyeColor colour);

    void markForUpdate();

    /** Null for a {@link ConnectedType#TILE} connection to a block with no block entity (a cauldron, a composter):
     *  reach the neighbour through its capabilities, not its tile. */
    @Nullable
    BlockEntity getConnectedTile(Direction side);

    IPipe getConnectedPipe(Direction side);

    boolean isConnected(Direction side);

    ConnectedType getConnectedType(Direction side);

    enum ConnectedType {
        TILE,
        PIPE
    }
}

