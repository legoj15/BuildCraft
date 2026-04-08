package buildcraft.api.transport.pipe;

import java.io.IOException;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipeHolder.IWriter;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

public abstract class PipeFlow {
    /** The ID for completely refreshing the state of this flow. */
    public static final int NET_ID_FULL_STATE = 0;
    /**
     * The ID for updating what has changed since the last NET_ID_FULL_STATE or
     * NET_ID_UPDATE has been sent.
     */
    // Wait, what? How is that a good idea or even sensible to make updates work
    // this way?
    public static final int NET_ID_UPDATE = 1;

    public final IPipe pipe;

    public PipeFlow(IPipe pipe) {
        this.pipe = pipe;
    }

    public PipeFlow(IPipe pipe, CompoundTag nbt) {
        this.pipe = pipe;
    }

    public CompoundTag writeToNbt() {
        return new CompoundTag();
    }

    /** Updates this flow's state from the given NBT. Used for in-place updates
     *  when the server sends a block entity sync packet. */
    public void readFromNbt(CompoundTag nbt) {
        // Default no-op — subclasses override to update their state
    }

    /**
     * Writes a payload with the specified id. Standard ID's are NET_ID_FULL_STATE
     * and NET_ID_UPDATE.
     */
    public void writePayload(int id, FriendlyByteBuf buffer, Object side) {
    }

    /**
     * Reads a payload with the specified id. Standard ID's are NET_ID_FULL_STATE
     * and NET_ID_UPDATE.
     */
    public void readPayload(int id, FriendlyByteBuf buffer, Object side) throws IOException {
    }

    public void sendPayload(int id) {
        final Object side = pipe.getHolder().getPipeWorld().isClientSide() ? null : null;
        sendCustomPayload(id, (buf) -> writePayload(id, buf, side));
    }

    public final void sendCustomPayload(int id, IWriter writer) {
        pipe.getHolder().sendMessage(PipeMessageReceiver.FLOW, buffer -> {
            buffer.writeBoolean(true);
            buffer.writeShort(id);
            writer.write(buffer);
        });
    }

    public abstract boolean canConnect(Direction face, PipeFlow other);

    public abstract boolean canConnect(Direction face, BlockEntity oTile);

    /**
     * Used to force a connection to a given tile, even if the {@link PipeBehaviour}
     * wouldn't normally connect to
     * it.
     */
    public boolean shouldForceConnection(Direction face, BlockEntity oTile) {
        return false;
    }

    public void onTick() {
    }

    public void postPluggableTick() {
    }

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
    }

    public boolean onFlowActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
            EnumPipePart part) {
        return false;
    }

    public final boolean hasCapability(@Nonnull Object capability, Direction facing) {
        return getCapability(capability, facing) != null;
    }

    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        return null;
    }
}
