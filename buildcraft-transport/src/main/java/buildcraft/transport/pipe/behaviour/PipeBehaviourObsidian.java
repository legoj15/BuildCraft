package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFlow;

/** Obsidian pipe — sucks up dropped items using MJ power. */
public class PipeBehaviourObsidian extends PipeBehaviour implements IMjRedstoneReceiver {
    private static final double SPEED = 0.04;
    private final MjBattery battery = new MjBattery(256 * MjAPI.MJ);

    public PipeBehaviourObsidian(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourObsidian(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        battery.deserializeNBT(nbt.getCompoundOrEmpty("battery"));
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("battery", battery.serializeNBT());
        return nbt;
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@Nonnull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return battery.getCapacity() - battery.getStored();
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return battery.addPowerChecking(microJoules, simulate);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourObsidian);
    }

    @Override
    public void onTick() {
        Level world = pipe.getHolder().getPipeWorld();
        if (world.isClientSide()) {
            return;
        }
        BlockPos pos = pipe.getHolder().getPipePos();

        // Find the unconnected face to pull from
        Direction pullDir = null;
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                pullDir = face;
                break;
            }
        }
        if (pullDir == null) {
            return;
        }

        long power = battery.getStored();
        if (power < MjAPI.MJ / 2) {
            return;
        }

        PipeFlow flow = pipe.getFlow();
        if (!(flow instanceof IFlowItems)) {
            return;
        }
        IFlowItems flowItems = (IFlowItems) flow;

        // Create AABB for entity pickup
        double range = 1.0 + (power / (double) (64 * MjAPI.MJ)) * 3.0;
        AABB entityBB = new AABB(pos).inflate(range);
        List<ItemEntity> entities = world.getEntitiesOfClass(ItemEntity.class, entityBB);

        for (ItemEntity entity : entities) {
            if (!entity.isAlive()) continue;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;

            long cost = MjAPI.MJ;
            if (battery.getStored() < cost) break;

            battery.extractPower(0, cost);
            flowItems.insertItemsForce(stack.copy(), pullDir, null, SPEED);
            entity.discard();
        }
    }
}
