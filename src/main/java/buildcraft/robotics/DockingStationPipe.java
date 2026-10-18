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

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
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
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IFlowRedstoneFlux;
import buildcraft.api.transport.pipe.IPipeHolder;
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
import buildcraft.transport.BCTransportConfig;
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
public class DockingStationPipe extends DockingStation implements IRequestProvider {

    private @Nullable IPipeHolder holder;

    public DockingStationPipe() {
        // Loading later from NBT — DO NOT TOUCH.
    }

    public DockingStationPipe(IPipeHolder holder, Direction side) {
        super(holder.getPipePos(), side);
        this.holder = holder;
        this.world = holder.getPipeWorld();
    }

    /** The pipe this station sits on, re-resolved from the world when the cached reference died (chunk reload, pipe
     *  rebuild) — 7.1.x's {@code getPipe()} re-fetch. Deregisters this station when its pipe is positively gone.
     *
     *  <ul>
     *  <li><b>Chunk not loaded:</b> the cached holder stands in, unchecked, and may be null (a station read back from
     *      the save that has not resolved yet). An unload {@code setRemoved}s the pipe tile, so the cache is stale but
     *      still the best answer: its gates' last actions keep a robot working away from home inside the zone they
     *      name. Nothing here may load the chunk: {@code Level.getBlockEntity} loads it synchronously on every node,
     *      and this is reached per robot tick ({@code isInitialized}) and per station in every search.</li>
     *  <li><b>Chunk loaded:</b> a cached holder whose block entity was REMOVED is dead even though it still holds its
     *      pipe. On 1.21.10+, {@code /fill} and {@code /setblock} place with
     *      {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}, so the pipe's removal hooks never run and its station is
     *      never told; a pipe rebuilt there adopts the registration ({@code RobotStationPluggable.onTick}). Trusting
     *      the cache read the removed pipe's gates for good, so the new pipe's Provide/Accept Items gates did nothing
     *      and its robots idled at home (7.1.x checked {@code isInvalid()} for the same reason). The live block entity
     *      is looked up instead; if it is no pipe, or a pipe without a station plug on this face, the station
     *      is gone and deregisters, which shuts down a robot homed here rather than leaving a ghost station in the
     *      save with its robot frozen.</li>
     *  </ul> */
    @Nullable
    public IPipeHolder getHolder() {
        if (!loaded()) {
            return holder;
        }
        if (holder == null || holder.getPipe() == null
                || (holder.getPipeTile() instanceof BlockEntity cached && cached.isRemoved())) {
            BlockEntity tile = world.getBlockEntity(getPos());
            // Only a pipe that still carries a station plug on this face is this station's pipe: /fill pipe-over-pipe
            // on 1.21.10+ leaves a plugless pipe here, and adopting it kept a gateless ghost registered (still chosen
            // by searches and docked at), where 1.21.1's /fill deregisters it. A rebuild that places the pipe and its
            // plug on different ticks heals: the new plug's first onTick registers a fresh station.
            if (!(tile instanceof IPipeHolder iph) || !(iph.getPluggable(side()) instanceof IDockingStationProvider)) {
                holder = null;
                RobotManager.registryProvider.getRegistry(world).removeStation(this);
                return null;
            }
            holder = iph;
        }
        if (holder.getPipe() == null) {
            RobotManager.registryProvider.getRegistry(world).removeStation(this);
            holder = null;
        }
        return holder;
    }

    /** Whoever placed the pipe this station sits on. */
    @Override
    @Nullable
    public GameProfile getOwner() {
        IPipeHolder h = getHolder();
        return h == null ? null : h.getOwner();
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
        // The face the cargo crosses the pipe's boundary through — the station's own face, so a
        // station on TOP visibly injects from above. 7.1.x passed side.getOpposite() into
        // PipeTransportItems.injectItem, but that parameter was the item's MOTION direction: 7.1.x
        // spawned it at centre + 0.2 toward the station and moved it inward (readjustPosition only
        // clamps, it never relocates to the input face). The modern injectItem/insertItemsForce
        // parameter instead means "the face the item starts from" (TravellingItem.side), so the
        // opposite-face translation inverted the visual — cargo appearing out of thin air on the far
        // side once the entry leg gained real animation. It also wrongly pre-tried the opposite face,
        // forbidding legitimate downward exits under a top-mounted station; 7.1.x blacklisted the
        // STATION face (input.getOpposite()), exactly what tried.add(side()) now does.
        return EnumPipePart.fromFacing(side());
    }

    @Override
    public net.minecraft.world.Container getItemInput() {
        Direction facing = extractionFacing();
        if (facing == null) {
            return null;
        }
        // The inventory this station's pipe draws from: the block one cell out along the WOODEN pipe's own
        // extraction face. A docked robot LOADS from here (D6).
        return loadedBlockEntity(getPos().relative(facing)) instanceof net.minecraft.world.Container container
                ? container : null;
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
        IPipeHolder h = getHolder();
        Direction facing = fluidExtractionFacing(h);
        if (facing == null || world == null || !world.isLoaded(getPos().relative(facing))) {
            return null;
        }
        // The fluid source this station's pipe draws from: the block one cell out along the WOODEN FLUID
        // pipe's own extraction face (resolved through the neighbour's block capability, with the
        // blocking-pluggable checks the item input's BlockEntity read doesn't need). A docked robot LOADS
        // fluid from here — 7.1.x's getFluidInput, which the fluid carrier board's load leg needs.
        return h.getCapabilityFromPipe(facing, CapUtil.CAP_FLUIDS);
    }

    @Override
    public EnumPipePart getFluidInputSide() {
        // The face of the TANK the pipe touches — the opposite of the direction searched, exactly as the
        // item input side (7.1.x returned the same opposite).
        Direction facing = fluidExtractionFacing(getHolder());
        return facing == null ? EnumPipePart.CENTER : EnumPipePart.fromFacing(facing.getOpposite());
    }

    /** The fluid twin of {@link #extractionFacing}: the extraction face of the wooden FLUID pipe hosting
     *  this station, or null if the pipe is not a fluid pipe, is not a wooden (extraction) pipe, or
     *  currently faces nothing. 7.1.x gated its {@code getFluidInput} on
     *  {@code getPipe() instanceof PipeFluidsWood}; {@link PipeBehaviourWood} is the shared behaviour of
     *  both the wood and diamond-wood fluid pipes here, so the filtered pipe qualifies exactly as 7.1.x's
     *  {@code PipeFluidsEmerald extends PipeFluidsWood} did — and an ITEM pipe never qualifies, keeping
     *  the item and fluid supply discoveries independent. */
    private static @Nullable Direction fluidExtractionFacing(@Nullable IPipeHolder h) {
        if (h == null || h.getPipe() == null || !(h.getPipe().getFlow() instanceof IFlowFluid)) {
            return null;
        }
        return h.getPipe().getBehaviour() instanceof PipeBehaviourWood wood ? wood.getCurrentDir() : null;
    }

    /** A station is a charger when its pipe carries power it can hand to a docked robot: MJ kinesis directly, or
     *  Forge-Energy (RF) kinesis through {@link #acceptsForgeEnergy()}. This is what {@code AIRobotRecharge}
     *  searches for and what {@code AIRobotMain} consults before shutting a docked robot down, so an RF station
     *  must say yes exactly when it can really deliver -- see {@link #acceptsForgeEnergy()}. */
    @Override
    public boolean providesPower() {
        IPipeHolder h = getHolder();
        boolean mjKinesis = h != null && h.getPipe() != null && h.getPipe().getFlow() instanceof IFlowPower;
        return mjKinesis || acceptsForgeEnergy();
    }

    /** Whether this station's pipe is an RF kinesis pipe whose Forge Energy the station may convert into a docked
     *  robot's battery (see {@code RobotStationPluggable#getInternalCapability}). Gated like every other BC
     *  machine's FE surface and read live: MJ&lt;-&gt;RF autoconversion must be on (nothing under
     *  {@code powerMode = MJ_ONLY}, whose contract is that machines take MJ exclusively), and the RF pipes must
     *  not be switched off by {@code disableRfPipe}. The first check after the flow type is the cheap, config-free
     *  one, so MJ and item stations never touch config. */
    public boolean acceptsForgeEnergy() {
        IPipeHolder h = getHolder();
        return h != null && h.getPipe() != null && h.getPipe().getFlow() instanceof IFlowRedstoneFlux
            && MjAPI.isRfAutoConversionEnabled()
            && !BCTransportConfig.disableRfPipe.get();
    }

    // ── Request network (Ph8) ─────────────────────────────────────────────
    // 7.1.x's two-half discovery, ported whole. The physical half: a Requester block beside the pipe is
    // found by scanning the host pipe's six neighbours (7.1.x scanned the pipe tile's neighbours for an
    // IRequestProvider — identity, not capability — and so does this). The virtual half: EVERY station is
    // itself a provider whose requests are the gate-side "Request Items" actions, which is what
    // makes that Ph6 statement actually do something. The fallback keeps getRequestProvider() non-null,
    // exactly as 7.1.x returned `this` — the delivery search polls every station as a provider.

    @Override
    public IRequestProvider getRequestProvider() {
        IPipeHolder h = getHolder();
        if (h != null && h.getPipe() != null) {
            for (Direction dir : Direction.values()) {
                if (loadedBlockEntity(getPos().relative(dir)) instanceof IRequestProvider provider) {
                    return provider;
                }
            }
        }
        return this;
    }

    /** 7.1.x's slot encoding, verbatim: {@code side} in bits 4-6, the gate's action-slot index in bits
     *  2-3, the action's parameter index in bits 0-1. The count 127 covers the encoding space (sides
     *  6-7 are dead rows no direction ever fills).
     *
     *  <p>Four action slots are enough: Request Items needs an action parameter, only the Quartz and Diamond
     *  modifiers add those, and both halve the slot count, so no gate that can hold the action has more than four
     *  slots (the 8-slot gates take no parameters). {@code RequestItemsSlotEncodingTest} pins that. */
    @Override
    public int getRequestsCount() {
        return 127;
    }

    @Override
    public ItemStack getRequest(int slot) {
        int sideIndex = (slot & 0x70) >> 4;
        // 7.1.x's ForgeDirection had a 7th UNKNOWN entry, so its dead encoding rows (side bits 6-7)
        // resolved to a gate that could never exist; Direction has exactly 6, so guard those rows here.
        if (sideIndex >= Direction.values().length) {
            return ItemStack.EMPTY;
        }
        Direction gateSide = Direction.values()[sideIndex];
        int action = (slot & 0xc) >> 2;
        int param = slot & 0x3;

        IPipeHolder h = getHolder();
        if (h == null || h.getPipe() == null
                || !(h.getPluggable(gateSide) instanceof PluggableGate gate)) {
            return ItemStack.EMPTY;
        }

        var actions = gate.logic.getActions();
        if (actions.size() <= action) {
            return ItemStack.EMPTY;
        }

        if (actions.get(action) != BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS) {
            return ItemStack.EMPTY;
        }

        // THIS slot's own parameters, and only while its trigger group is firing. 7.1.x took the first ACTIVE
        // action of the same kind instead, so every Request Items slot after the first read the first one's
        // parameters and its own requests never surfaced.
        if (!gate.logic.actionOn[action]) {
            return ItemStack.EMPTY;
        }
        IStatementParameter[] parameters = gate.logic.statements[action].action.getParameters();
        if (parameters.length <= param || parameters[param] == null) {
            return ItemStack.EMPTY;
        }

        return parameters[param].getItemStack();
    }

    /** 7.1.x's virtual offer pushed the stack straight into the pipe transport; this routes through the
     *  same force-path station output the unload AIs use. The injection face is the station's own face
     *  (see {@link #getItemOutputSide()}), so delivered orders enter the pipe from the station side. The
     *  injectable claims full acceptance (the force path has no backpressure), so the excess is always
     *  empty here. */
    @Override
    public ItemStack offerItem(int slot, ItemStack stack) {
        IInjectable output = getItemOutput();
        if (output == null) {
            return stack;
        }
        return output.injectItem(stack, true, side(), null, 0.0);
    }

    /** Whether the station is usable: its pipe is there, or its chunk is simply not loaded. An unloaded home is
     *  ordinary (a robot working away from it on a server) and must not freeze the robot, which
     *  {@code EntityRobot.serverTick} does while its home reads uninitialized; before {@link #getHolder} stopped
     *  loading chunks, the lookup quietly loaded the home's chunk to answer yes. Usable is not reachable: station
     *  searches also skip a station whose chunk is not loaded ({@link #isChunkLoaded()}). */
    @Override
    public boolean isInitialized() {
        return getHolder() != null || !loaded();
    }

    /** Whether this station's chunk is loaded; a station not yet given a world counts as not loaded. Not
     *  {@link #isChunkLoaded()}, which lets a world-less test double count as loaded. */
    private boolean loaded() {
        return world != null && world.isLoaded(getPos());
    }

    /** The block entity at {@code pos}, or null while its chunk is not loaded: {@code Level.getBlockEntity} would
     *  load the chunk synchronously to answer. */
    private @Nullable BlockEntity loadedBlockEntity(BlockPos pos) {
        return world != null && world.isLoaded(pos) ? world.getBlockEntity(pos) : null;
    }

    /** 7.1.x's {@code getPipe(); if (pipe == null) return false;} guard: a station whose pipe is gone
     *  cannot be reserved, and deregisters itself on the way out. Backstop only — every removal path now
     *  runs the pluggable's {@code onRemove} — but a station that somehow outlives its pipe must not stay
     *  a flight destination forever. */
    @Override
    public boolean take(EntityRobotBase robot) {
        return !isPipeGone() && super.take(robot);
    }

    @Override
    public boolean takeAsMain(EntityRobotBase robot) {
        return !isPipeGone() && super.takeAsMain(robot);
    }

    /** True only when the world POSITIVELY says there is no pipe here any more.
     *
     *  <p>Deliberately not {@code getHolder() == null}: that also answers "gone" for a live pipe whose
     *  {@code getPipe()} is momentarily null (a tile mid-load, a holder placed but not yet given its pipe),
     *  and a reservation refused on that transient reading strands the robot — {@code AIRobotGotoStation}
     *  treats a refused take as a failed move and sends the board to sleep. Measured: routing take() through
     *  {@code getHolder()} made the sleep/wakeup gate test fail roughly two runs in five. Asking the world
     *  whether the block entity is still an {@link IPipeHolder} has neither the false positive nor
     *  {@code getHolder()}'s deregistering side effect. A pipe without a station plug on this face is not this
     *  station's pipe ({@link #getHolder}). A station whose chunk is not loaded is not gone (and asking would load the
     *  chunk). */
    private boolean isPipeGone() {
        if (world == null || !world.isLoaded(getPos())) {
            return false;
        }
        if (world.getBlockEntity(getPos()) instanceof IPipeHolder iph
                && iph.getPluggable(side()) instanceof IDockingStationProvider) {
            return false;
        }
        RobotManager.registryProvider.getRegistry(world).removeStation(this);
        holder = null;
        return true;
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
        return ActionRobotFilter.canInteractWithItem(this, stack, ActionStationProvideItems.class)
                && ActionStationProvideItems.canExtractItem(this, stack);
    }

    @Override
    public boolean canRobotAcceptItem(ItemStack stack) {
        return ActionRobotFilter.canInteractWithItem(this, stack, ActionStationAcceptItems.class);
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
}
