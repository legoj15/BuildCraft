package buildcraft.api.mj;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.NBTUtilBC;



/** Provides a basic implementation of a simple battery. Note that you should call {@link #tick(Level, BlockPos)} or
 * {@link #tick(Level, Vec3)} every tick to allow for losing excess power. */
public class MjBattery  {
    private final long capacity;
    private long microJoules = 0;

    public MjBattery(long capacity) {
        this.capacity = capacity;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("stored", microJoules);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        setStored(NBTUtilBC.getLong(nbt, "stored", 0L));
    }

    public void writeToBuffer(ByteBuf buffer) {
        buffer.writeLong(microJoules);
    }

    public void readFromBuffer(ByteBuf buffer) {
        setStored(buffer.readLong());
    }

    /** Replace the stored power level absolutely. Clamps to {@code [0, capacity]}. Use this from
     *  sync / load paths (NBT deserialise, network packet apply) where the incoming value is the
     *  authoritative server snapshot, not a delta. The additive {@link #addPower} would silently
     *  accumulate past capacity if called repeatedly with the same authoritative value, which is
     *  exactly what every-5-tick block-entity sync does — that bug manifested as a Builder
     *  client battery climbing to ~65× capacity within ~13 seconds while the server stayed at 0,
     *  triggering phantom laser firing on the client because {@code SnapshotBuilder.clientTick}
     *  uses {@code stored > 0} to gate animation extrapolation. */
    public void setStored(long microJoules) {
        if (microJoules < 0) {
            this.microJoules = 0;
        } else if (microJoules > capacity) {
            this.microJoules = capacity;
        } else {
            this.microJoules = microJoules;
        }
    }

    /** Adds power to the battery, capped at {@link #getCapacity()}.
     *
     * @param microJoulesToAdd The power to add. A negative value removes power — sync paths use that, so negative
     *            amounts pass straight through and are never treated as overflow.
     * @param simulate If true, report the result without modifying the battery.
     * @return The excess power that did not fit: {@code 0} when all of it was accepted (and {@code 0} when removing
     *         power), otherwise the amount over capacity. */
    public long addPower(long microJoulesToAdd, boolean simulate) {
        long accepted = microJoulesToAdd;
        if (microJoulesToAdd > 0) {
            long room = Math.max(0L, capacity - microJoules);
            accepted = Math.min(microJoulesToAdd, room);
        }
        if (!simulate) {
            this.microJoules += accepted;
        }
        return microJoulesToAdd - accepted;
    }

    /** Attempts to add power, but only if this is not already full.
     * 
     * @param microJoulesToAdd The power to add.
     * @return The excess power. */
    public long addPowerChecking(long microJoulesToAdd, boolean simulate) {
        if (isFull()) {
            return microJoulesToAdd;
        } else {
            return addPower(microJoulesToAdd, simulate);
        }
    }

    public long extractAll() {
        return extractPower(0, microJoules);
    }

    /** Attempts to extract exactly the given amount of power.
     * 
     * @param power The amount of power to extract.
     * @return True if the power was removed, false if not. */
    public boolean extractPower(long power) {
        return extractPower(power, power) > 0;
    }

    public long extractPower(long min, long max) {
        if (microJoules < min) return 0;
        long extracting = Math.min(microJoules, max);
        microJoules -= extracting;
        return extracting;
    }

    public boolean isFull() {
        return microJoules >= capacity;
    }

    public long getStored() {
        return microJoules;
    }

    public long getCapacity() {
        return capacity;
    }

    public void tick(Level world, BlockPos position) {
        tick(world, new Vec3(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5));
    }

    public void tick(Level world, Vec3 position) {
        if (microJoules > capacity * 2) {
            losePower(world, position);
        }
    }

    protected void losePower(Level world, Vec3 position) {
        long diff = microJoules - capacity * 2;
        long lost = ceilDivide(diff, 32);
        microJoules -= lost;
        MjAPI.EFFECT_MANAGER.createPowerLossEffect(world, position, lost);
    }

    private static long ceilDivide(long val, long by) {
        return (val / by) + (val % by == 0 ? 0 : 1);
    }

    public String getDebugString() {
        return MjAPI.formatMj(microJoules) + " / " + MjAPI.formatMj(capacity) + " MJ";
    }
}

