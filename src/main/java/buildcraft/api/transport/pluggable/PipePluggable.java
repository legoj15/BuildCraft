package buildcraft.api.transport.pluggable;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;


import net.neoforged.neoforge.capabilities.ICapabilityProvider;




import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

public abstract class PipePluggable {
    public final PluggableDefinition definition;
    public final IPipeHolder holder;
    public final Direction side;

    public PipePluggable(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        this.definition = definition;
        this.holder = holder;
        this.side = side;
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        return nbt;
    }

    /** Attempts to update this pluggable in-place from NBT data, preserving the existing object
     * identity. This is critical for GUI containers that hold direct references to the pluggable
     * or its internal logic — recreating the pluggable from scratch would orphan those references.
     *
     * @return true if the pluggable was updated in-place, false if a full recreation is needed. */
    public boolean readFromNbt(CompoundTag nbt) {
        return false;
    }

    /** Writes the payload that will be passed into
     * {@link PluggableDefinition#loadFromBuffer(IPipeHolder, Direction, FriendlyByteBuf)} on the client. (This is called
     * on the server and sent to the client). Note that this will be called *instead* of write and read payload. */
    public void writeCreationPayload(FriendlyByteBuf buffer) {

    }

    public void writePayload(FriendlyByteBuf buffer, Object side) {

    }

    public void readPayload(FriendlyByteBuf buffer, Object side, Object ctx) throws IOException {

    }

    public final void scheduleNetworkUpdate() {
        holder.scheduleNetworkUpdate(PipeMessageReceiver.PLUGGABLES[side.ordinal()]);
    }

    public void onTick() {}

    /** @return A bounding box that will be used for collisions and raytracing. */
    public abstract AABB getBoundingBox();

    /** @return True if the pipe cannot connect outwards (it is blocked), or False if this does not block the pipe. */
    public boolean isBlocking() {
        return false;
    }

    /** Gets the value of a specified capability key, or null if the given capability is not supported at the call time.
     * This is effectively {@link ICapabilityProvider}, but where
     * {@link ICapabilityProvider#hasCapability(Object, Direction)} will return true when this returns a non-null
     * value. */
    public <T> T getCapability(@Nonnull Object cap) {
        return null;
    }

    /** Gets the {@link Object} that is accessible from the pipe that this is attached to.
     * 
     * @param cap
     * @return */
    public <T> T getInternalCapability(@Nonnull Object cap) {
        return null;
    }

    /** Called whenever this pluggable is removed from the pipe. */
    public void onRemove() {}

    /** @param toDrop A list containing all the items to drop (so you should add your items to this list) */
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        ItemStack stack = getPickStack();
        if (!stack.isEmpty()) {
            toDrop.add(stack);
        }
    }

    /** Called whenever this pluggable is picked by the player (similar to Block.getPickBlock)
     * 
     * @return The stack that should be picked, or ItemStack.EMPTY if no stack can be picked from this pluggable. */
    public ItemStack getPickStack() {
        return ItemStack.EMPTY;
    }

    public boolean onPluggableActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ) {
        return false;
    }

    @Nullable
    public PluggableModelKey getModelRenderKey(Object layer) {
        return null;
    }

    /** Called if the {@link IPluggableStaticBaker} returns quads with tint indexes set to
     * <code>data * 6 + key.side.ordinal()</code>. <code>"data"</code> is passed in here as <code>"tintIndex"</code>.
     * 
     * @return The tint index to render the quad with, or -1 for default. */
    
    public int getBlockColor(int tintIndex) {
        return -1;
    }

    /** PipePluggable version of
     * {@link Block#canBeConnectedTo(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, Direction)}. */
    public boolean canBeConnected() {
        return false;
    }

    /** PipePluggable version of
     * {@link net.minecraft.world.level.block.state.BlockState#isSideSolid(BlockGetter, BlockPos, Direction)} */
    public boolean isSideSolid() {
        return false;
    }

    /** PipePluggable version of {@link Block#getExplosionResistance(Level, BlockPos, Entity, Explosion)} */
    public float getExplosionResistance(@Nullable Entity exploder, Explosion explosion) {
        return 0;
    }

    public boolean canConnectToRedstone(@Nullable Direction to) {
        return false;
    }

    /** PipePluggable version of
     * {@link net.minecraft.world.level.block.state.BlockState#getBlockFaceShape(BlockGetter, BlockPos, Direction)} */
    public Object getBlockFaceShape() {
        return null;
    }

    public void onPlacedBy(Player player) {

    }
}

