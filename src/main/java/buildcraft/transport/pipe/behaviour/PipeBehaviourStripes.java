/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventActionActivate;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeEventStatement;
import buildcraft.api.transport.pipe.PipeFlow;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.mj.MjRedstoneBatteryReceiver;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RefusalMemory;

import buildcraft.transport.BCTransportStatements;

public class PipeBehaviourStripes extends PipeBehaviour implements IStripesActivator, IMjRedstoneReceiver {
    private final MjBattery battery = new MjBattery(256 * MjAPI.MJ);
    // The behaviour must itself be an IMjRedstoneReceiver (the pulsar plug + power-adaptor placement test
    // the pipe behaviour directly via getBehaviour() instanceof IMjRedstoneReceiver), so it can't extend
    // the shared receiver — instead it holds one and delegates, single-sourcing the battery-receiver logic.
    private final MjRedstoneBatteryReceiver mjReceiver = new MjRedstoneBatteryReceiver(battery);

    @Nullable
    public Direction direction = null;
    /** The power put into the current break cycle; only ever spent on the block {@link #refusals} permits. */
    private long progress;
    /** What protection mods said (policy {@link RefusalMemory#STRIPES}): refused blocks are left alone instead of
     *  re-charged every cycle, and the permit is the block (position and state) the current break cycle is about,
     *  which {@link #progress} belongs to. The pipe being re-pointed, or the block vanishing or being replaced, no
     *  longer matches the permit and starts a fresh cycle that asks first; before, a stale answer and progress carried
     *  over, so the pipe charged and cracked a block nobody had asked about. */
    private final RefusalMemory refusals = new RefusalMemory(RefusalMemory.STRIPES);
    /** The block this pipe's break cracks are drawn on (the {@code destroyBlockProgress} overlay), or null while none
     *  is up. Kept apart from the permit: the permit follows the owner, so it can be gone while the crack it drew is
     *  still on screen. */
    @Nullable
    private BlockPos crackedPos;

    public PipeBehaviourStripes(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourStripes(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        battery.deserializeNbt(NBTUtilBC.getCompound(nbt, "battery"));
        direction = NBTUtilBC.readEnum(nbt.get("direction"), Direction.class);
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("battery", battery.serializeNbt());
        if (direction != null) {
            nbt.put("direction", NBTUtilBC.writeEnum(direction));
        }
        return nbt;
    }

    @Override
    public void readFromNbt(CompoundTag nbt) {
        super.readFromNbt(nbt);
        battery.deserializeNbt(NBTUtilBC.getCompound(nbt, "battery"));
        direction = NBTUtilBC.readEnum(nbt.get("direction"), Direction.class);
    }

    @Override
    public void readPayload(FriendlyByteBuf buffer, Object ctx) throws java.io.IOException {
        super.readPayload(buffer, ctx);
        int dirOrd = buffer.readByte();
        direction = (dirOrd >= 0 && dirOrd < 6) ? Direction.values()[dirOrd] : null;
    }

    @Override
    public void writePayload(FriendlyByteBuf buffer) {
        super.writePayload(buffer);
        buffer.writeByte(direction == null ? -1 : direction.ordinal());
    }

    /** The power put into breaking the block in front so far this cycle (for tests). */
    public long getBreakProgress() {
        return progress;
    }

    /** The block the pipe currently has a break crack drawn on, or null (for tests). */
    @Nullable
    public BlockPos getCrackedPos() {
        return crackedPos;
    }

    // Direction management

    private void setDirection(@Nullable Direction newValue) {
        if (direction != newValue) {
            direction = newValue;
            if (!pipe.getHolder().getPipeWorld().isClientSide()) {
                pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
            }
        }
    }

    // Statement actions

    @PipeEventHandler
    public void addInternalActions(PipeEventStatement.AddActionInternal event) {
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                PipePluggable plug = pipe.getHolder().getPluggable(face);
                if (plug == null || !plug.isBlocking()) {
                    event.actions.add(BCTransportStatements.ACTION_PIPE_DIRECTION[face.ordinal()]);
                }
            }
        }
    }

    @PipeEventHandler
    public void onActionActivate(PipeEventActionActivate event) {
        for (Direction face : Direction.values()) {
            if (event.action == BCTransportStatements.ACTION_PIPE_DIRECTION[face.ordinal()]) {
                setDirection(face);
            }
        }
    }

    // IMjRedstoneReceiver — delegated to the shared MjRedstoneBatteryReceiver over this behaviour's battery.

    @Override
    public boolean canConnect(@Nonnull IMjConnector other) {
        return mjReceiver.canConnect(other);
    }

    @Override
    public long getPowerRequested() {
        return mjReceiver.getPowerRequested();
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return mjReceiver.receivePower(microJoules, simulate);
    }

    // Pipe behaviour

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourStripes);
    }

    @Override
    public void onTick() {
        Level world = pipe.getHolder().getPipeWorld();
        BlockPos pos = pipe.getHolder().getPipePos();
        if (world.isClientSide()) {
            return;
        }
        if (direction == null || pipe.isConnected(direction)) {
            int sides = 0;
            Direction dir = null;
            for (Direction face : Direction.values()) {
                if (pipe.isConnected(face)) {
                    sides++;
                    dir = face;
                }
            }
            if (sides == 1) {
                setDirection(dir.getOpposite());
            } else {
                setDirection(null);
            }
        }
        battery.tick(world, pipe.getHolder().getPipePos());
        if (direction != null) {
            BlockPos offset = pos.relative(direction);
            BlockState state = world.getBlockState(offset);
            GameProfile owner = pipe.getHolder().getOwner();
            if (!refusals.isPermitted(offset, state, owner)) {
                endCycle(world); // not the block the progress is for
            }
            long target = BlockUtil.computeBlockBreakPower(world, offset);
            // A block protection mods refused is left alone (no power, no asking) until it changes or the retry
            // window passes: 1.12.2 re-charged the whole break and re-asked every cycle for as long as the claim stood.
            if (target > 0 && !refusals.isRefused(world, offset, owner)) {
                int offsetHash = offset.hashCode();
                if (progress < target) {
                    // Ask before the first power of a cycle, so a refused block costs none; the answer holds for the
                    // cycle (the permit), and the break itself asks once more in case a claim appeared meanwhile.
                    if (!refusals.isPermitted(offset, state, owner)) {
                        if (!mayBreak((ServerLevel) world, offset)) {
                            return;
                        }
                        refusals.permit(offset, state, owner);
                    }
                    progress += battery.extractPower(0, Math.min(target - progress, MjAPI.MJ * 10));
                    if (progress > 0) {
                        world.destroyBlockProgress(offsetHash, offset, (int) (progress * 9 / target));
                        crackedPos = offset;
                    }
                } else if (mayBreak((ServerLevel) world, offset)) {
                    BlockUtil.breakBlockAndGetDropsWithXp(
                        (ServerLevel) world,
                        offset,
                        new ItemStack(Items.DIAMOND_PICKAXE),
                        pipe.getHolder().getOwner()
                    ).ifPresent(result -> {
                        result.drops().forEach(stack -> sendItem(stack, direction));
                        // XP at the broken-block position rather than the pipe — the stripes
                        // pipe is a thin transient digger, the player is usually right where
                        // the block was, and there's no "machine block" the orb should follow.
                        if (result.xp() > 0) {
                            world.getBlockState(offset).getBlock()
                                .popExperience((ServerLevel) world, offset, result.xp());
                        }
                    });
                    progress = 0;
                    refusals.revokePermit();
                }
            }
        } else {
            endCycle(world);
        }
    }

    /** Drops the current cycle: its progress, the crack it drew, and the permit. The crack is cleared on the block it
     *  was drawn on, not on the permitted one: a change of owner revokes the permit before the cycle is dropped. */
    private void endCycle(Level world) {
        if (crackedPos != null) {
            world.destroyBlockProgress(crackedPos.hashCode(), crackedPos, -1);
            crackedPos = null;
        }
        progress = 0;
        refusals.revokePermit();
    }

    /**
     * Asks protection mods whether the pipe may break the block at {@code offset}, as the pipe's owner (like the
     * quarry and builder do). A refusal is remembered and the cycle is dropped: the block stays, the crack clears and
     * the spent power is gone, as 1.12.2 reset its progress when the owner's fake player had its break event
     * cancelled - but the pipe now leaves that block alone afterwards, for the {@link RefusalMemory#STRIPES} window.
     */
    private boolean mayBreak(ServerLevel world, BlockPos offset) {
        if (BlockUtil.canMachineBreak(world, offset, pipe.getHolder().getOwner())) {
            return true;
        }
        endCycle(world);
        refusals.remember(world, offset, pipe.getHolder().getOwner());
        return false;
    }

    @PipeEventHandler
    public void onDrop(PipeEventItem.Drop event) {
        if (direction == null) {
            return;
        }
        IPipeHolder holder = pipe.getHolder();
        Level world = holder.getPipeWorld();
        BlockPos pos = holder.getPipePos();
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) {
            return;
        }
        // The owner's leased fake player (the "[BuildCraft]" one for an unowned pipe), reset and centred on the
        // pipe. The travelling stack goes in its hand; on success everything left in its inventory is sent back
        // down the pipe, and closing the lease drops the stack reference a failed handler leaves behind (the
        // stack itself carries on as the dropped item).
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(serverLevel, holder.getOwner(), pos)) {
            FakePlayer player = lease.player();
            player.setItemInHand(InteractionHand.MAIN_HAND, event.getStack());
            if (PipeApi.stripeRegistry != null &&
                PipeApi.stripeRegistry.handleItem(world, pos, direction, event.getStack(), player, this)) {
                event.setStack(ItemStack.EMPTY);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().removeItemNoUpdate(i);
                    if (!stack.isEmpty()) {
                        sendItem(stack, direction);
                    }
                }
            }
        }
    }

    // IStripesActivator

    @Override
    public void dropItem(@Nonnull ItemStack stack, Direction direction) {
        InventoryUtil.drop(pipe.getHolder().getPipeWorld(), pipe.getHolder().getPipePos(), stack);
    }

    @Override
    public boolean sendItem(@Nonnull ItemStack stack, Direction from) {
        PipeFlow flow = pipe.getFlow();
        if (flow instanceof IFlowItems) {
            ((IFlowItems) flow).insertItemsForce(stack, from, null, 0.02);
            return true;
        } else {
            return false;
        }
    }

    // Capabilities

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        if (capability == MjAPI.CAP_REDSTONE_RECEIVER) {
            return (T) this;
        }
        if (capability == MjAPI.CAP_RECEIVER) {
            return (T) this;
        }
        if (capability == MjAPI.CAP_CONNECTOR) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }
}
