/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.Collections;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.lib.misc.CapUtil;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;

import org.jspecify.annotations.Nullable;

/**
 * Docking station attached to a pipe via {@link RobotStationPluggable}, ported from 7.1.x
 * {@code buildcraft.robotics.DockingStationPipe}. Exposes the pipe's item/fluid flow as the station's
 * output for a docked robot to unload cargo into, and re-resolves its {@link IPipeHolder} lazily since
 * the pluggable (and therefore the holder reference) is rebuilt on every world/chunk load.
 *
 * <p>Gate-triggered station actions ({@code getActiveActions}) are Ph6 (statements port) — no gate query
 * plumbing exists yet, so this always reports no active actions.
 */
public class DockingStationPipe extends DockingStation {

    private @Nullable IPipeHolder holder;

    public DockingStationPipe() {
        // Loading later from NBT — DO NOT TOUCH.
    }

    public DockingStationPipe(IPipeHolder holder, Direction side) {
        super(holder.getPipePos(), side);
        this.holder = holder;
        this.world = holder.getPipeWorld();
    }

    /** Re-resolves the holder from the world if the cached reference died (chunk reload, pipe rebuild) —
     *  mirrors 7.1.x's {@code getPipe()} re-fetch. Deregisters this station if the pipe is genuinely gone. */
    @Nullable
    public IPipeHolder getHolder() {
        if (holder == null || holder.getPipe() == null) {
            BlockEntity tile = world.getBlockEntity(getPos());
            holder = tile instanceof IPipeHolder iph ? iph : null;
        }
        if (holder != null && holder.getPipe() == null) {
            RobotManager.registryProvider.getRegistry(world).removeStation(this);
            holder = null;
        }
        return holder;
    }

    @Override
    public Iterable<StatementSlot> getActiveActions() {
        return Collections.emptyList();
    }

    @Override
    public IInjectable getItemOutput() {
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null) {
            return null;
        }
        return h.getPipe().getFlow() instanceof IFlowItems items ? items : null;
    }

    @Override
    public EnumPipePart getItemOutputSide() {
        return EnumPipePart.fromFacing(side().getOpposite());
    }

    @Override
    public net.minecraft.world.Container getItemInput() {
        Direction facing = extractionFacing();
        if (facing == null) {
            return null;
        }
        // The inventory this station's pipe draws from: the block one cell out along the WOODEN pipe's own
        // extraction face. A docked robot LOADS from here (D6).
        BlockEntity tile = world.getBlockEntity(getPos().relative(facing));
        return tile instanceof net.minecraft.world.Container container ? container : null;
    }

    @Override
    public EnumPipePart getItemInputSide() {
        // The face of the INVENTORY that the pipe touches — the opposite of the direction we searched in.
        // 7.1.x returned the same (`ForgeDirection.getOrientation(meta).getOpposite()`); an earlier port
        // returned the search direction itself, which is the inward face, not the one to extract through.
        Direction facing = extractionFacing();
        return facing == null ? EnumPipePart.CENTER : EnumPipePart.fromFacing(facing.getOpposite());
    }

    /** The extraction face of the wooden pipe hosting this station, or null if the pipe is not an item
     *  pipe, is not a wooden (extraction) pipe, or currently faces nothing.
     *
     * <p>Both halves of that check are load-bearing and were both missing until 2026-08-06. 7.1.x gated
     * this on {@code getPipe().getPipe() instanceof PipeItemsWood} and read the pipe's own block-metadata
     * orientation. The port instead accepted ANY {@code IFlowItems} pipe and searched along
     * {@code side().getOpposite()} — the station's own face. That was wrong twice over, and in-client
     * testing caught it: a robot station on a plain cobblestone pipe let a Carrier drain the chest below
     * it, i.e. a robot station became a free, unpowered hopper on any pipe at all; and a wooden pipe
     * wrenched to face a chest to the north with the station plugged on top read whatever sat below it
     * instead of the chest it was pointed at. Requiring {@link PipeBehaviourWood} restores BuildCraft's
     * standing rule that pulling out of an inventory takes an extraction pipe, and reading
     * {@code getCurrentDir()} makes the wrench mean what it looks like it means.
     *
     * <p>{@code PipeBehaviourWoodDiamond} extends {@code PipeBehaviourWood}, so the filtered wooden pipe
     * qualifies too — exactly as 7.1.x's {@code PipeItemsEmerald extends PipeItemsWood} did. */
    private @Nullable Direction extractionFacing() {
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowItems)) {
            return null;
        }
        return h.getPipe().getBehaviour() instanceof PipeBehaviourWood wood ? wood.getCurrentDir() : null;
    }

    //? if >=1.21.10 {
    @Override
    public ResourceHandler<FluidResource> getFluidOutput() {
    //?} else {
    /*@Override
    public IFluidHandler getFluidOutput() {
    *///?}
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowFluid)) {
            return null;
        }
        return h.getPipe().getFlow().getCapability(CapUtil.CAP_FLUIDS, side().getOpposite());
    }

    @Override
    public EnumPipePart getFluidOutputSide() {
        return EnumPipePart.CENTER;
    }

    @Override
    public boolean providesPower() {
        IPipeHolder h = getHolder();
        return h != null && h.getPipe() != null && h.getPipe().getFlow() instanceof IFlowPower;
    }

    @Override
    public boolean isInitialized() {
        return getHolder() != null;
    }

    // take/takeAsMain/unsafeRelease deliberately do NOT nudge the renderer. They used to call
    // scheduleRenderUpdate(), which was doubly wrong: it forces a chunk re-mesh that
    // KeyPlugRobotStation exists specifically to avoid (the reserved/linked pedestal is re-emitted
    // per frame, not baked), and it never actually reached the client anyway, since the state is
    // derived from this server-side SavedData and appears in neither the pluggable's NBT nor its
    // update tag.
    // RobotStationPluggable.onTick() now detects the transition and pushes a single state byte.

    @Override
    public void onChunkUnload() {
        holder = null;
    }
}
