/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.lib.inventory.filter.ArrayStackOrListFilter;
import buildcraft.lib.inventory.filter.PassThroughStackFilter;
import buildcraft.lib.misc.CapUtil;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.ActionRobotFilterTool;
import buildcraft.robotics.statements.ActionStationAcceptFluids;
import buildcraft.robotics.statements.ActionStationAcceptItems;
import buildcraft.robotics.statements.ActionStationForbidRobot;
import buildcraft.robotics.statements.ActionStationProvideFluids;
import buildcraft.robotics.statements.ActionStationProvideItems;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.flow.PipeFlowFluids;

import org.jspecify.annotations.Nullable;

/**
 * Docking station attached to a pipe via {@link RobotStationPluggable}, ported from 7.1.x
 * {@code buildcraft.robotics.DockingStationPipe}. Exposes the pipe's item/fluid flow as the station's
 * output for a docked robot to unload cargo into, and re-resolves its {@link IPipeHolder} lazily since
 * the pluggable (and therefore the holder reference) is rebuilt on every world/chunk load.
 *
 * <p>Ph6 wires the gate actions: {@link #getActiveActions()} aggregates every {@link PluggableGate}'s
 * resolved actions across the pipe's six faces (7.1.x's {@code ActionIterator} over the pipe's gates),
 * and the D1 policy methods consult them — so a station refuses item/fluid interaction unless its gates
 * hold the matching provide/accept actions, exactly as 7.1.x refused gateless stations.
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
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null) {
            return Collections.emptyList();
        }
        // 7.1.x's ActionIterator flattened every gate on the pipe (one per face); the modern holder
        // exposes the same per-side pluggables, so a fresh snapshot per call is the honest equivalent
        // (each consumer iterates once, and the gates' lists re-resolve every tick anyway).
        List<StatementSlot> actions = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (h.getPluggable(dir) instanceof PluggableGate gate) {
                actions.addAll(gate.logic.getActiveActions());
            }
        }
        return actions;
    }

    /** 7.1.x's station-side injectable, which the port must synthesize rather than inherit: a robot
     *  docked at this station is a valid item source BY VIRTUE OF BEING DOCKED, so this
     *  {@code canInjectItems} never consults the pipe network — 7.1.x returned true unconditionally and
     *  pushed the {@code TravelingItem} straight into the transport. Exposing the raw
     *  {@code PipeFlowItems} instead would inherit its {@code canInjectItems} =
     *  {@code pipe.isConnected(from)}, which refuses everything whenever the pipe has no connection on
     *  the face opposite the station — i.e. every ordinary dead-end dock, so a picker's "unloads them
     *  at a station" silently never happened. Injection goes through {@code insertItemsForce}, the
     *  modern equivalent of 7.1.x's direct {@code PipeTransportItems.injectItem(item, from)}. */
    private final IInjectable itemOutput = new IInjectable() {
        @Override
        public boolean canInjectItems(Direction from) {
            IPipeHolder h = getHolder();
            return h != null && h.getPipe() != null && h.getPipe().getFlow() instanceof IFlowItems;
        }

        @Override
        public net.minecraft.world.item.ItemStack injectItem(net.minecraft.world.item.ItemStack stack,
                boolean doAdd, Direction from, net.minecraft.world.item.DyeColor colour, double speed) {
            IPipeHolder h = getHolder();
            if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowItems flow)) {
                return stack;
            }
            if (doAdd && !stack.isEmpty()) {
                flow.insertItemsForce(stack.copy(), from, colour, speed);
            }
            // Fully accepted, as 7.1.x's wrapper reported stackSize accepted — including on the dry-run,
            // where the modern leftover-convention answer to "how much would you take" is "all of it".
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    };

    @Override
    public IInjectable getItemOutput() {
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowItems)) {
            return null;
        }
        return itemOutput;
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
        if (h == null || h.getPipe() == null
                || !(h.getPipe().getFlow() instanceof PipeFlowFluids flow)) {
            return null;
        }
        // NOT the pipe's per-face CAP_FLUIDS handler, for the same reason the item output above is not
        // the raw PipeFlowItems: that handler's insert is gated on pipe.isConnected(face), so with the
        // station on the UP face every unload bailed unless something happened to be connected to the
        // pipe's DOWN face — i.e. every ordinary dead-end dock, which made a Pump or Tank robot's
        // "unloads at a station" silently never happen. A robot docked here is a valid fluid source by
        // virtue of being docked; getStationOutput is the force path, and unlike the pipe's own
        // capability it honours the transaction, so the unload AIs' per-station dry runs stay
        // observational.
        return flow.getStationOutput(side().getOpposite());
    }

    @Override
    public EnumPipePart getFluidOutputSide() {
        return EnumPipePart.CENTER;
    }

    //? if >=1.21.10 {
    @Override
    public ResourceHandler<FluidResource> getFluidInput() {
    //?} else {
    /*@Override
    public IFluidHandler getFluidInput() {
    *///?}
        Direction facing = fluidExtractionFacing();
        if (facing == null) {
            return null;
        }
        // The fluid source this station's pipe draws from: the block one cell out along the WOODEN FLUID
        // pipe's own extraction face (resolved through the neighbour's block capability, with the
        // blocking-pluggable checks the item input's BlockEntity read doesn't need). A docked robot LOADS
        // fluid from here — 7.1.x's getFluidInput, which the fluid carrier board's load leg needs.
        return getHolder().getCapabilityFromPipe(facing, CapUtil.CAP_FLUIDS);
    }

    @Override
    public EnumPipePart getFluidInputSide() {
        // The face of the TANK the pipe touches — the opposite of the direction searched, exactly as the
        // item input side (7.1.x returned the same opposite).
        Direction facing = fluidExtractionFacing();
        return facing == null ? EnumPipePart.CENTER : EnumPipePart.fromFacing(facing.getOpposite());
    }

    /** The fluid twin of {@link #extractionFacing}: the extraction face of the wooden FLUID pipe hosting
     *  this station, or null if the pipe is not a fluid pipe, is not a wooden (extraction) pipe, or
     *  currently faces nothing. 7.1.x gated its {@code getFluidInput} on
     *  {@code getPipe() instanceof PipeFluidsWood}; {@link PipeBehaviourWood} is the shared behaviour of
     *  both the wood and diamond-wood fluid pipes here, so the filtered pipe qualifies exactly as 7.1.x's
     *  {@code PipeFluidsEmerald extends PipeFluidsWood} did — and an ITEM pipe never qualifies, keeping
     *  the item and fluid supply discoveries independent. */
    private @Nullable Direction fluidExtractionFacing() {
        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowFluid)) {
            return null;
        }
        return h.getPipe().getBehaviour() instanceof PipeBehaviourWood wood ? wood.getCurrentDir() : null;
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

    // ── D1 station-policy overrides (Ph6) ─────────────────────────────────
    // The base class keeps permissive defaults as the GATELESS contract for non-pipe stations and test
    // doubles; this pipe-backed station implements 7.1.x's real gate semantics, where an action class
    // with no matching active statement refuses (7.1.x ActionRobotFilter: actionFound=false with no
    // actions), and an action with an empty filter set passes everything.

    @Override
    public boolean canRobotExtractItem(ItemStack stack) {
        return ActionRobotFilter.canInteractWithItem(this,
                new ArrayStackOrListFilter(stack), ActionStationProvideItems.class)
                && ActionStationProvideItems.canExtractItem(this, stack);
    }

    @Override
    public boolean canRobotAcceptItem(ItemStack stack) {
        return ActionRobotFilter.canInteractWithItem(this,
                new ArrayStackOrListFilter(stack), ActionStationAcceptItems.class);
    }

    @Override
    public boolean isRobotForbidden(IRobotAccess robot) {
        return ActionStationForbidRobot.isForbidden(robot, this);
    }

    @Override
    public IStackFilter getRobotItemFilter() {
        IStackFilter filter = ActionRobotFilter.getGateFilter(this);
        if (filter instanceof PassThroughStackFilter) {
            // No work-filter set — fall back to the tool filter (7.1.x ActionRobotFilterTool), which is
            // itself pass-through when unset: a robot fetches anything a gate doesn't restrict.
            filter = ActionRobotFilterTool.getGateFilter(this);
        }
        return filter;
    }

    @Override
    public IFluidFilter getRobotFluidFilter() {
        // No tool-filter twin exists on the fluid side (7.1.x's Filter Tool was item-only), so this is
        // the work filter alone — pass-through when the gate sets no filter.
        return ActionRobotFilter.getGateFluidFilter(this);
    }

    @Override
    public boolean canRobotExtractFluid(IFluidFilter filter) {
        return ActionRobotFilter.canInteractWithFluid(this, filter, ActionStationProvideFluids.class);
    }

    @Override
    public boolean canRobotAcceptFluid(IFluidFilter filter) {
        return ActionRobotFilter.canInteractWithFluid(this, filter, ActionStationAcceptFluids.class);
    }

    @Override
    public void onChunkUnload() {
        holder = null;
    }
}
