/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.items.FluidItemDrops;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.FluidTransferInfo;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventFluid.OnMoveToCentre;
import buildcraft.api.transport.pipe.PipeEventFluid.PreMoveToCentre;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventStatement;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MathUtil;

import buildcraft.core.BCCoreConfig;
import buildcraft.transport.BCTransportStatements;

@SuppressWarnings("unchecked")
public class PipeFlowFluids extends PipeFlow implements IFlowFluid, IDebuggable {

    private static final int DIRECTION_COOLDOWN = 60;
    private static final int COOLDOWN_INPUT = -DIRECTION_COOLDOWN;
    private static final int COOLDOWN_OUTPUT = DIRECTION_COOLDOWN;

    public static final int NET_FLUID_AMOUNTS = 2;

    /** The number of pixels the fluid moves by per millisecond */
    public static final double FLOW_MULTIPLIER = 0.016;

    private final FluidTransferInfo fluidTransferInfo = PipeApi.getFluidTransferInfo(pipe.getDefinition());

    public final int capacity = Math.max(1000, fluidTransferInfo.transferPerTick * 10);// TEMP!

    private final Map<EnumPipePart, Section> sections = new EnumMap<>(EnumPipePart.class);
    @Nonnull
    private FluidStack currentFluid = FluidStack.EMPTY;
    private int currentDelay;
    private SafeTimeTracker tracker;

    private SafeTimeTracker getTracker() {
        if (tracker == null) {
            tracker = new SafeTimeTracker(BCCoreConfig.networkUpdateRate.get(), 4);
        }
        return tracker;
    }

    // Client fields for interpolating amounts
    private long lastMessage, lastMessageMinus1;

    // === Render cache (client-side only) ===
    // Populated and read by PipeFlowRendererFluids. Stored on the flow so the cache
    // lifetime matches the pipe's lifetime; invalidated by reference-comparing
    // {@link #renderCacheFluid} against the current fluid in the renderer.
    // Field types are shared-side classes (Fluid, Identifier) so this is safe on
    // dedicated servers — the fields are simply never touched there.
    public transient net.minecraft.world.level.material.Fluid renderCacheFluid;
    public transient net.minecraft.resources.Identifier renderCacheSpriteId;
    public transient int renderCacheTintR = 0xFF;
    public transient int renderCacheTintG = 0xFF;
    public transient int renderCacheTintB = 0xFF;
    public transient int renderCacheTintA = 0xFF;
    public transient boolean renderCacheTranslucent;

    public PipeFlowFluids(IPipe pipe) {
        super(pipe);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
    }

    public PipeFlowFluids(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
        if (nbt.contains("fluid_id")) {
            // Deserialize fluid using registry key string (no HolderLookup.Provider needed)
            String fluidId = nbt.getStringOr("fluid_id", "");
            if (!fluidId.isEmpty()) {
                net.minecraft.resources.Identifier fluidRL =
                    net.minecraft.resources.Identifier.parse(fluidId);
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(fluidRL);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    setFluid(new FluidStack(fluid, 1000));
                } else {
                    setFluid(FluidStack.EMPTY);
                }
            } else {
                setFluid(FluidStack.EMPTY);
            }
        } else {
            setFluid(FluidStack.EMPTY);
        }

        for (EnumPipePart part : EnumPipePart.VALUES) {
            int direction = part.getIndex();
            String key = "tank[" + direction + "]";
            if (nbt.contains(key)) {
                CompoundTag compound = nbt.getCompoundOrEmpty(key);
                sections.get(part).readFromNbt(compound);
            }
        }
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();

        if (!currentFluid.isEmpty()) {
            // Serialize fluid using registry key string (no HolderLookup.Provider needed)
            nbt.putString("fluid_id",
                net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(currentFluid.getFluid()).toString());

            for (EnumPipePart part : EnumPipePart.VALUES) {
                int direction = part.getIndex();
                CompoundTag subTag = new CompoundTag();
                sections.get(part).writeToNbt(subTag);
                nbt.put("tank[" + direction + "]", subTag);
            }
        }

        return nbt;
    }

    /** Updates this flow's fluid data in-place from the given NBT.
     *  Used for client-side sync — avoids recreating the PipeFlowFluids object. */
    @Override
    public void readFromNbt(CompoundTag nbt) {
        if (nbt.contains("fluid_id")) {
            String fluidId = nbt.getStringOr("fluid_id", "");
            if (!fluidId.isEmpty()) {
                net.minecraft.resources.Identifier fluidRL =
                    net.minecraft.resources.Identifier.parse(fluidId);
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(fluidRL);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    // Set fluid directly without calling setFluid() to avoid resetting section state
                    currentFluid = new FluidStack(fluid, 1000);
                } else {
                    currentFluid = FluidStack.EMPTY;
                }
            } else {
                currentFluid = FluidStack.EMPTY;
            }
        } else {
            currentFluid = FluidStack.EMPTY;
        }
        // Update section amounts
        for (EnumPipePart part : EnumPipePart.VALUES) {
            int direction = part.getIndex();
            String key = "tank[" + direction + "]";
            if (nbt.contains(key)) {
                CompoundTag compound = nbt.getCompoundOrEmpty(key);
                sections.get(part).readFromNbt(compound);
            }
        }
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof IFlowFluid;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        return pipe.getHolder().getCapabilityFromPipe(face, CapUtil.CAP_FLUIDS) != null;
    }

    @Override
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        if (capability == CapUtil.CAP_FLUIDS) {
            return (T) sections.get(EnumPipePart.fromFacing(facing));
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        super.addDrops(toDrop, fortune);
        if (!currentFluid.isEmpty()) {
            int totalAmount = 0;
            for (EnumPipePart part : EnumPipePart.VALUES) {
                totalAmount += sections.get(part).amount;
            }
            if (totalAmount > 0) {
                FluidItemDrops.addFluidDrops(toDrop, currentFluid.copyWithAmount(totalAmount));
            }
        }
    }

    public boolean doesContainFluid() {
        for (EnumPipePart part : EnumPipePart.VALUES) {
            if (sections.get(part).amount > 0) {
                return true;
            }
        }
        return false;
    }


    @PipeEventHandler
    public static void addTriggers(PipeEventStatement.AddTriggerInternal event) {
        event.triggers.add(BCTransportStatements.TRIGGER_FLUIDS_TRAVERSING);
    }

    // IFlowFluid

    @Override
    @SuppressWarnings("unchecked")
    public FluidStack tryExtractFluid(int millibuckets, Direction from, FluidStack filter, boolean simulate) {
        if (from == null || millibuckets <= 0) {
            return null;
        }
        ResourceHandler<FluidResource> handler = pipe.getHolder().getCapabilityFromPipe(from, CapUtil.CAP_FLUIDS);
        if (handler == null) {
            return null;
        }
        Section section = sections.get(EnumPipePart.fromFacing(from));
        Section middle = sections.get(EnumPipePart.CENTER);
        millibuckets = Math.min(millibuckets, capacity * 2 - section.amount - middle.amount);
        if (millibuckets <= 0) {
            return null;
        }

        // Determine what fluid resource to extract
        FluidResource resource;
        if (filter != null && !filter.isEmpty()) {
            resource = FluidResource.of(filter);
        } else {
            // Extract whatever the handler has
            resource = handler.getResource(0);
            if (resource == null || resource.isEmpty()) {
                return null;
            }
        }

        int extracted;
        if (simulate) {
            try (Transaction tx = Transaction.openRoot()) {
                extracted = handler.extract(resource, millibuckets, tx);
                // tx is not committed — rolls back automatically (simulate)
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                extracted = handler.extract(resource, millibuckets, tx);
                tx.commit();
            }
        }

        if (extracted <= 0) {
            return null;
        }

        FluidStack toAdd = resource.toStack(extracted);
        if (currentFluid.isEmpty() && !simulate) {
            setFluid(toAdd);
        }
        int reallyFilled = section.fillInternal(extracted, !simulate);
        int leftOver = extracted - reallyFilled;
        reallyFilled += middle.fillInternal(leftOver, !simulate);
        if (!simulate) {
            section.ticksInDirection = COOLDOWN_INPUT;
        }
        if (reallyFilled != extracted) {
            BCLog.logger.warn(
                "[tryExtractFluid] Filled "
                + reallyFilled + " != extracted " + extracted
                + " @" + pipe.getHolder().getPipePos()
            );
        }
        return toAdd;
    }

    @Override
    public Object tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter, boolean simulate) {
        // Advanced extraction — simplified port, uses basic extraction
        return tryExtractFluid(millibuckets, from, null, simulate);
    }

    @Override
    public int insertFluidsForce(FluidStack fluid, @Nullable Direction from, boolean simulate) {
        Section s = sections.get(EnumPipePart.CENTER);
        if (fluid == null || fluid.isEmpty()) {
            return 0;
        }
        if (!currentFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(currentFluid, fluid)) {
            return 0;
        }
        if (currentFluid.isEmpty() && !simulate) {
            setFluid(fluid.copy());
        }
        int filled = s.fill(fluid.getAmount(), !simulate);
        if (filled == 0) {
            return 0;
        }
        if (simulate) {
            return filled;
        }
        if (from != null) {
            sections.get(EnumPipePart.fromFacing(from)).ticksInDirection = COOLDOWN_INPUT;
        }
        return filled;
    }

    @Override
    @Nullable
    public FluidStack extractFluidsForce(int min, int max, @Nullable Direction section, boolean simulate) {
        if (min > max) {
            throw new IllegalArgumentException("Minimum (" + min + ") > maximum (" + max + ")");
        }
        if (max < 0) {
            return null;
        }
        Section s = sections.get(EnumPipePart.fromFacing(section));
        if (s.amount < min) {
            return null;
        }
        int amount = MathUtil.clamp(s.amount, min, max);
        FluidStack fluid = currentFluid.copyWithAmount(amount);
        if (!simulate) {
            s.amount -= amount;
            s.drainInternal(amount, false);
            if (s.amount == 0) {
                boolean isEmpty = true;
                for (Section s2 : sections.values()) {
                    isEmpty &= s2.amount == 0;
                }
                if (isEmpty) {
                    setFluid(FluidStack.EMPTY);
                }
            }
        }
        return fluid;
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        boolean isClient = pipe.getHolder().getPipeWorld().isClientSide();

        left.add(" - FluidType = " + (currentFluid.isEmpty() ? "empty" : currentFluid.getHoverName().getString()));

        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section section = sections.get(part);
            if (section == null) {
                continue;
            }
            StringBuilder line = new StringBuilder(" - ");
            line.append(part.face == null ? "center" : part.face.getName());
            line.append(" = ");
            int amount = isClient ? section.target : section.amount;
            line.append(amount > 0 ? ChatFormatting.GREEN : "");
            line.append(amount).append("").append(ChatFormatting.RESET).append("mB");
            line.append(" ").append(section.getCurrentDirection()).append(" (").append(section.ticksInDirection).append(
                ")"
            );

            line.append(" [");
            int last = -1;
            int skipped = 0;

            for (int i : section.incoming) {
                if (i != last) {
                    if (skipped > 0) {
                        line.append("...").append(skipped).append("... ");
                        skipped = 0;
                    }
                    last = i;
                    line.append(i).append(", ");
                } else {
                    skipped++;
                }
            }
            if (skipped > 0) {
                line.append("...").append(skipped).append("... ");
            }
            line.append("0]");

            left.add(line.toString());
        }
    }

    // Rendering

    /** Returns the current fluid type for rendering, or null if empty. */
    public FluidStack getFluidStackForRender() {
        return currentFluid.isEmpty() ? null : currentFluid;
    }

    /** Writes per-section interpolated fluid amounts into {@code out} (length 7).
     *  Index follows {@link EnumPipePart#getIndex()}: 0-5 = facings, 6 = CENTER.
     *  Caller supplies the buffer to avoid the per-frame {@code double[7]} allocation. */
    public void writeAmountsForRender(float partialTicks, double[] out) {
        boolean clientSide = pipe.getHolder().getPipeWorld().isClientSide();
        double pt = partialTicks;
        double invPt = 1 - partialTicks;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section s = sections.get(part);
            int i = part.getIndex();
            if (clientSide) {
                out[i] = s.clientAmountLast * invPt + s.clientAmountThis * pt;
            } else {
                out[i] = s.amount;
            }
        }
    }

    /** Writes per-section interpolated flow offsets into the three component arrays
     *  (each length 7). Index follows {@link EnumPipePart#getIndex()}. Caller
     *  supplies buffers to avoid per-frame allocation of a {@code Vec3[]} plus
     *  21 transient {@code Vec3}. The section's offset state is stored as
     *  primitive doubles (see {@link Section#offsetThisX} etc.), so default
     *  values (0) are correct before {@code tickClient} runs for the first time. */
    public void writeOffsetsForRender(float partialTicks, double[] outX, double[] outY, double[] outZ) {
        double pt = partialTicks;
        double invPt = 1 - partialTicks;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section s = sections.get(part);
            int i = part.getIndex();
            outX[i] = s.offsetLastX * invPt + s.offsetThisX * pt;
            outY[i] = s.offsetLastY * invPt + s.offsetThisY * pt;
            outZ[i] = s.offsetLastZ * invPt + s.offsetThisZ * pt;
        }
    }

    // Internal logic

    private void setFluid(@Nonnull FluidStack fluid) {
        currentFluid = fluid;
        if (!fluid.isEmpty()) {
            currentDelay = (int) PipeApi.getFluidTransferInfo(pipe.getDefinition()).transferDelayMultiplier;
        } else {
            currentDelay = (int) PipeApi.getFluidTransferInfo(pipe.getDefinition()).transferDelayMultiplier;
        }
        for (Section section : sections.values()) {
            section.incoming = new int[currentDelay];
            section.currentTime = 0;
            section.ticksInDirection = 0;
        }
        // NOTE: Do NOT call pipe.getHolder().scheduleRenderUpdate() here, even though it looks
        // like the right place to push the fluid-type change to the client. scheduleRenderUpdate
        // queues a *full* BlockEntity NBT sync via level.sendBlockUpdated(), and on the client
        // TilePipeHolder.onDataPacket re-invokes level.sendBlockUpdated() — which marks the
        // pipe's chunk section dirty and forces a chunk-mesh rebuild. When a pipe is bouncing
        // fluid against a full downstream sink (e.g. a combustion engine that's at capacity),
        // every empty↔non-empty transition went through this path: two chunk rebuilds per
        // bounce per pipe in the path, producing the visible lag spike "every time the gas
        // moves into the next pipe." The lightweight MessagePipePayload (NET_FLUID_AMOUNTS)
        // already covers everything the renderer needs: writePayload writes the fluid registry
        // id when currentFluid is non-empty, and the per-section amount loop fires whenever
        // section.amount differs from lastSentAmount — which is exactly the condition that
        // setFluid is paired with (empty → non-empty when fluid arrives at section.fill, and
        // non-empty → empty when totalFluid drops to 0 in onTick). The pipe's *block model*
        // doesn't depend on fluid state — connections / paint / pluggables drive the model,
        // and those have their own scheduleRenderUpdate calls — so the chunk-rebuild was
        // pure waste in the fluid path.
    }

    @Override
    public void onTick() {
        Level world = pipe.getHolder().getPipeWorld();
        if (world.isClientSide()) {
            for (EnumPipePart part : EnumPipePart.VALUES) {
                sections.get(part).tickClient();
            }
            return;
        }

        if (!currentFluid.isEmpty()) {
            int totalFluid = 0;
            boolean canOutput = false;

            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                section.currentTime = (section.currentTime + 1) % currentDelay;
                section.advanceForMovement();
                totalFluid += section.amount;
                if (section.getCurrentDirection().canOutput()) {
                    canOutput = true;
                }
            }
            if (totalFluid == 0) {
                setFluid(FluidStack.EMPTY);
            } else {
                if (canOutput) {
                    moveFromPipe();
                }
                moveFromCenter();
                moveToCenter();
            }

            // tick cooldowns
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                if (section.ticksInDirection > 0) {
                    section.ticksInDirection--;
                } else if (section.ticksInDirection < 0) {
                    section.ticksInDirection++;
                }
            }
        }

        boolean send = false;

        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section section = sections.get(part);
            if (section.amount != section.lastSentAmount) {
                send = true;
                break;
            } else {
                Dir should = Dir.get(section.ticksInDirection);
                if (section.lastSentDirection != should) {
                    send = true;
                    break;
                }
            }
        }

        if (send && getTracker().markTimeIfDelay(world)) {
            // Send lightweight custom payload instead of full NBT block entity sync
            sendPayload(NET_FLUID_AMOUNTS);
        }
    }

    @SuppressWarnings("unchecked")
    private void moveFromPipe() {
        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            if (section.getCurrentDirection().canOutput()) {
                int maxDrain = section.drainInternal(fluidTransferInfo.transferPerTick, false);
                if (maxDrain <= 0) {
                    continue;
                }
                PipeEventFluid.SideCheck sideCheck = new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid);
                sideCheck.disallowAllExcept(part.face);
                pipe.getHolder().fireEvent(sideCheck);
                if (sideCheck.getOrder().size() == 1) {
                    ResourceHandler<FluidResource> handler = pipe.getHolder().getCapabilityFromPipe(part.face, CapUtil.CAP_FLUIDS);
                    if (handler == null) continue;

                    if (maxDrain > 0) {
                        FluidResource resource = FluidResource.of(currentFluid);
                        int filled;
                        try (Transaction tx = Transaction.openRoot()) {
                            filled = handler.insert(resource, maxDrain, tx);
                            tx.commit();
                        }
                        if (filled > 0) {
                            section.drainInternal(filled, true);
                            section.ticksInDirection = COOLDOWN_OUTPUT;
                        }
                    }
                }
            }
        }
    }

    private void moveFromCenter() {
        Section center = sections.get(EnumPipePart.CENTER);
        int totalAvailable = center.getMaxDrained();
        if (totalAvailable < 1) {
            return;
        }

        int flowRate = fluidTransferInfo.transferPerTick;
        Set<Direction> realDirections = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.values()) {
            Section section = sections.get(EnumPipePart.fromFacing(direction));
            if (!section.getCurrentDirection().canOutput()) {
                continue;
            }
            if (
                section.getMaxFilled() > 0
                && pipe.getHolder().getCapabilityFromPipe(direction, CapUtil.CAP_FLUIDS) != null
            ) {
                realDirections.add(direction);
            }
        }

        if (realDirections.size() > 0) {
            PipeEventFluid.SideCheck sideCheck = new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid);
            sideCheck.disallowAllExcept(realDirections);
            pipe.getHolder().fireEvent(sideCheck);

            EnumSet<Direction> set = sideCheck.getOrder();

            List<Direction> random = new ArrayList<>(set);
            Collections.shuffle(random);

            float min = Math.min(flowRate * realDirections.size(), totalAvailable)
            / (float) flowRate / realDirections.size();

            for (Direction direction : random) {
                Section section = sections.get(EnumPipePart.fromFacing(direction));
                int available = section.fill(flowRate, false);
                int amountToPush = (int) (available * min);
                if (amountToPush < 1) {
                    amountToPush++;
                }

                amountToPush = center.drainInternal(amountToPush, false);
                if (amountToPush > 0) {
                    int filled = section.fill(amountToPush, true);
                    if (filled > 0) {
                        center.drainInternal(filled, true);
                        section.ticksInDirection = COOLDOWN_OUTPUT;
                    }
                }
            }
        }
    }

    private void moveToCenter() {
        int transferInCount = 0;
        Section center = sections.get(EnumPipePart.CENTER);
        int spaceAvailable = capacity - center.amount;
        if (spaceAvailable <= 0 || center.getMaxFilled() <= 0) {
            return;
        }
        int flowRate = fluidTransferInfo.transferPerTick;

        List<EnumPipePart> faces = new ArrayList<>();
        Collections.addAll(faces, EnumPipePart.FACES);
        Collections.shuffle(faces);

        int[] inputPerTick = new int[6];
        for (EnumPipePart part : faces) {
            Section section = sections.get(part);
            inputPerTick[part.getIndex()] = 0;
            if (section.getCurrentDirection().canInput()) {
                inputPerTick[part.getIndex()] = section.drainInternal(flowRate, false);
                if (inputPerTick[part.getIndex()] > 0) {
                    transferInCount++;
                }
            }
        }

        int[] totalOffered = Arrays.copyOf(inputPerTick, 6);
        PreMoveToCentre preMove = new PreMoveToCentre(
            pipe.getHolder(), this, currentFluid, Math.min(flowRate, spaceAvailable), totalOffered, inputPerTick
        );
        pipe.getHolder().fireEvent(preMove);

        int[] fluidLeavingSide = new int[6];

        int left = Math.min(flowRate, spaceAvailable);
        float min = Math.min(flowRate * transferInCount, spaceAvailable) / (float) flowRate / transferInCount;
        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            int i = part.getIndex();
            if (inputPerTick[i] > 0) {
                int amountToDrain = (int) (inputPerTick[i] * min);
                if (amountToDrain < 1) {
                    amountToDrain++;
                }
                if (amountToDrain > left) {
                    amountToDrain = left;
                }
                int amountToPush = section.drainInternal(amountToDrain, false);
                if (amountToPush > 0) {
                    fluidLeavingSide[i] = amountToPush;
                    left -= amountToPush;
                }
            }
        }

        int[] fluidEnteringCentre = Arrays.copyOf(fluidLeavingSide, 6);
        OnMoveToCentre move = new OnMoveToCentre(
            pipe.getHolder(), this, currentFluid, fluidLeavingSide, fluidEnteringCentre
        );
        pipe.getHolder().fireEvent(move);

        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            int i = part.getIndex();
            int leaving = fluidLeavingSide[i];
            if (leaving > 0) {
                int actuallyDrained = section.drainInternal(leaving, true);
                if (actuallyDrained != leaving) {
                    throw new IllegalStateException(
                        "Couldn't drain " + leaving + " from " + part + ", only drained " + actuallyDrained
                    );
                }
                if (actuallyDrained > 0) {
                    section.ticksInDirection = COOLDOWN_INPUT;
                }
                int entering = fluidEnteringCentre[i];
                if (entering > 0) {
                    int actuallyFilled = center.fill(entering, true);
                    if (actuallyFilled != entering) {
                        throw new IllegalStateException(
                            "Couldn't fill " + entering + " from " + part + ", only filled " + actuallyFilled
                        );
                    }
                }
            }
        }
    }

    @Override
    public void writePayload(int id, FriendlyByteBuf buffer, Object side) {
        if (id == NET_FLUID_AMOUNTS || id == NET_ID_FULL_STATE) {
            boolean full = id == NET_ID_FULL_STATE;
            if (currentFluid.isEmpty()) {
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(true);
                // Simplified networking: write fluid ID inline instead of cache
                buffer.writeUtf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(currentFluid.getFluid()).toString());
            }
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                if (full) {
                    buffer.writeShort(section.amount);
                } else if (section.amount == section.lastSentAmount) {
                    buffer.writeBoolean(false);
                } else {
                    buffer.writeBoolean(true);
                    buffer.writeShort(section.amount);
                    section.lastSentAmount = section.amount;
                }
                Dir should = Dir.get(section.ticksInDirection);
                buffer.writeEnum(should);
                section.lastSentDirection = should;
            }
        }
    }

    @Override
    public void readPayload(int id, FriendlyByteBuf buffer, Object side) throws IOException {
        if (id == NET_FLUID_AMOUNTS || id == NET_ID_FULL_STATE) {
            boolean full = id == NET_ID_FULL_STATE;
            if (buffer.readBoolean()) {
                String fluidId = buffer.readUtf();
                // Client-side fluid lookup from registry — simplified
                net.minecraft.resources.Identifier fluidRL =
                    net.minecraft.resources.Identifier.parse(fluidId);
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(fluidRL);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    currentFluid = new FluidStack(fluid, 1000);
                }
            }
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                if (full || buffer.readBoolean()) {
                    section.target = buffer.readShort();
                    if (full) {
                        section.clientAmountLast = section.clientAmountThis = section.target;
                    }
                }

                Dir dir = buffer.readEnum(Dir.class);
                section.ticksInDirection = dir == Dir.NONE ? 0 : dir == Dir.IN ? COOLDOWN_INPUT : COOLDOWN_OUTPUT;
            }
            lastMessageMinus1 = lastMessage;
            lastMessage = pipe.getHolder().getPipeWorld().getGameTime();
        }
    }

    /** Holds data about a single section of this pipe. */
    class Section implements ResourceHandler<FluidResource> {
        final EnumPipePart part;

        int amount = 0;

        int lastSentAmount = 0;

        Dir lastSentDirection = Dir.NONE;

        int currentTime = 0;

        /** Map of [time] -> [amount inserted]. Used to implement the delayed fluid travelling. */
        int[] incoming = new int[1];

        int incomingTotalCache = 0;

        int ticksInDirection = 0;

        // Client side fields
        int clientAmountThis, clientAmountLast;
        int target = 0;
        // Interpolated flow-animation offset, stored as primitives so tickClient
        // produces zero garbage. Values start at 0 and are bounded to [-0.5, 0.5]
        // by the wrap step below; the previous Vec3 fields produced ~17 Vec3
        // allocations per CENTER tick + ~3 per face tick, which added up on
        // pipe-heavy networks.
        double offsetLastX, offsetLastY, offsetLastZ;
        double offsetThisX, offsetThisY, offsetThisZ;

        Section(EnumPipePart part) {
            this.part = part;
        }

        void writeToNbt(CompoundTag nbt) {
            nbt.putShort("capacity", (short) amount);
            nbt.putShort("lastSentAmount", (short) lastSentAmount);
            nbt.putShort("ticksInDirection", (short) ticksInDirection);

            for (int i = 0; i < incoming.length; ++i) {
                nbt.putShort("in[" + i + "]", (short) incoming[i]);
            }
        }

        void readFromNbt(CompoundTag nbt) {
            this.amount = nbt.getShortOr("capacity", (short) 0);
            this.lastSentAmount = nbt.getShortOr("lastSentAmount", (short) 0);
            this.ticksInDirection = nbt.getShortOr("ticksInDirection", (short) 0);
            // Initialize client interpolation fields so fluid is visible immediately
            // on chunk load, before any custom network packets arrive
            this.target = this.amount;
            this.clientAmountThis = this.amount;
            this.clientAmountLast = this.amount;

            incomingTotalCache = 0;
            for (int i = 0; i < incoming.length; ++i) {
                incomingTotalCache += incoming[i] = nbt.getShortOr("in[" + i + "]", (short) 0);
            }
        }

        int getMaxFilled() {
            int availableTotal = capacity - amount;
            int availableThisTick = fluidTransferInfo.transferPerTick - incoming[currentTime];
            return Math.min(availableTotal, availableThisTick);
        }

        int getMaxDrained() {
            return Math.min(amount - incomingTotalCache, fluidTransferInfo.transferPerTick);
        }

        int fill(int maxFill, boolean doFill) {
            int amountToFill = Math.min(getMaxFilled(), maxFill);
            if (amountToFill <= 0) {
                return 0;
            }
            if (doFill) {
                incoming[currentTime] += amountToFill;
                incomingTotalCache += amountToFill;
                amount += amountToFill;
            }
            return amountToFill;
        }

        public int fillInternal(int maxFill, boolean doFill) {
            int amountToFill = Math.min(capacity - amount, maxFill);
            if (amountToFill <= 0) {
                return 0;
            }
            if (doFill) {
                incoming[currentTime] += amountToFill;
                incomingTotalCache += amountToFill;
                amount += amountToFill;
            }
            return amountToFill;
        }

        int drainInternal(int maxDrain, boolean doDrain) {
            maxDrain = Math.min(maxDrain, getMaxDrained());
            if (maxDrain <= 0) {
                return 0;
            } else {
                if (doDrain) {
                    amount -= maxDrain;
                }
                return maxDrain;
            }
        }

        void advanceForMovement() {
            incomingTotalCache -= incoming[currentTime];
            incoming[currentTime] = 0;
        }

        void setTime(int current) {
            currentTime = current;
        }

        Dir getCurrentDirection() {
            return ticksInDirection == 0 ? Dir.NONE : ticksInDirection < 0 ? Dir.IN : Dir.OUT;
        }

        boolean tickClient() {
            clientAmountLast = clientAmountThis;

            if (target != clientAmountThis) {
                int delta = target - clientAmountThis;
                long msgDelta = lastMessage - lastMessageMinus1;
                msgDelta = MathUtil.clamp((int) msgDelta, 1, 60);
                if (Math.abs(delta) < msgDelta) {
                    clientAmountThis += delta;
                } else {
                    clientAmountThis += delta / (int) msgDelta;
                }
            }

            // When the section has been empty for two ticks, snap the animation
            // offset back to zero — matches the prior null-check / Vec3.ZERO
            // path on the previous Vec3 fields.
            if (clientAmountThis == 0 && clientAmountLast == 0) {
                offsetThisX = 0;
                offsetThisY = 0;
                offsetThisZ = 0;
            }
            // offsetLast = offsetThis (carry the previous tick's value forward
            // for partialTicks interpolation in writeOffsetsForRender).
            offsetLastX = offsetThisX;
            offsetLastY = offsetThisY;
            offsetLastZ = offsetThisZ;

            if (part.face == null) {
                // Centre: sum the unit step vectors of every face section with
                // an active flow direction, then signum each component so the
                // centre animates along the dominant axis regardless of
                // simultaneous in/out flows.
                double dirX = 0, dirY = 0, dirZ = 0;
                for (EnumPipePart p : EnumPipePart.FACES) {
                    Section s = sections.get(p);
                    if (s.ticksInDirection > 0) {
                        dirX += p.face.getStepX();
                        dirY += p.face.getStepY();
                        dirZ += p.face.getStepZ();
                    }
                }
                for (EnumPipePart p : EnumPipePart.FACES) {
                    Section s = sections.get(p);
                    if (s.ticksInDirection < 0) {
                        dirX -= p.face.getStepX();
                        dirY -= p.face.getStepY();
                        dirZ -= p.face.getStepZ();
                    }
                }
                dirX = Math.signum(dirX);
                dirY = Math.signum(dirY);
                dirZ = Math.signum(dirZ);
                offsetThisX += dirX * -FLOW_MULTIPLIER;
                offsetThisY += dirY * -FLOW_MULTIPLIER;
                offsetThisZ += dirZ * -FLOW_MULTIPLIER;
            } else {
                // Face section: advance along its face axis, sign chosen by
                // whether ticksInDirection marks the section as input or output.
                double mult = Math.signum(ticksInDirection);
                double delta = -FLOW_MULTIPLIER * mult;
                offsetThisX = offsetLastX + part.face.getStepX() * delta;
                offsetThisY = offsetLastY + part.face.getStepY() * delta;
                offsetThisZ = offsetLastZ + part.face.getStepZ() * delta;
            }

            // Wrap into [-0.5, 0.5] when the offset crosses ±0.5; shift both
            // last and this by the same amount so the interpolation remains
            // continuous across the wrap.
            double dx = offsetThisX >= 0.5 ? -1 : offsetThisX <= -0.5 ? 1 : 0;
            double dy = offsetThisY >= 0.5 ? -1 : offsetThisY <= -0.5 ? 1 : 0;
            double dz = offsetThisZ >= 0.5 ? -1 : offsetThisZ <= -0.5 ? 1 : 0;
            if (dx != 0 || dy != 0 || dz != 0) {
                offsetThisX += dx; offsetThisY += dy; offsetThisZ += dz;
                offsetLastX += dx; offsetLastY += dy; offsetLastZ += dz;
            }
            return clientAmountThis > 0 | clientAmountLast > 0;
        }

        // ResourceHandler<FluidResource>

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            if (index == 0 && !currentFluid.isEmpty() && amount > 0) {
                return FluidResource.of(currentFluid);
            }
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return index == 0 ? amount : 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return index == 0 ? capacity : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0) return false;
            return currentFluid.isEmpty()
                || FluidStack.isSameFluidSameComponents(currentFluid, resource.toStack(1));
        }

        @Override
        public int insert(int index, FluidResource resource, int insertAmount,
                          net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (index != 0) return 0;
            FluidStack fluidStack = resource.toStack(insertAmount);
            if (!getCurrentDirection().canInput() || !pipe.isConnected(part.face) || fluidStack.isEmpty()) {
                return 0;
            }
            PipeEventFluid.TryInsert tryInsert = new PipeEventFluid.TryInsert(
                pipe.getHolder(), PipeFlowFluids.this, part.face, fluidStack
            );
            pipe.getHolder().fireEvent(tryInsert);
            if (tryInsert.isCanceled()) {
                return 0;
            }

            if (currentFluid.isEmpty() || FluidStack.isSameFluidSameComponents(currentFluid, fluidStack)) {
                // BC manages its own state — commit changes immediately within the transaction
                if (currentFluid.isEmpty()) {
                    setFluid(fluidStack.copy());
                }
                int filled = fill(insertAmount, true);
                if (filled > 0) {
                    ticksInDirection = COOLDOWN_INPUT;
                }
                return filled;
            }
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int extractAmount,
                           net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            // Pipe sections don't support external drain
            return 0;
        }
    }

    /** Enum used for the current direction that a fluid is flowing. */
    enum Dir {
        IN(-1),
        NONE(0),
        OUT(1);

        final byte nbtValue;

        private Dir(int nbtValue) {
            this.nbtValue = (byte) nbtValue;
        }

        public boolean isInput() {
            return this == IN;
        }

        public boolean canInput() {
            return this != OUT;
        }

        public boolean isOutput() {
            return this == OUT;
        }

        public boolean canOutput() {
            return this != IN;
        }

        public static Dir get(int dir) {
            if (dir == 0) {
                return Dir.NONE;
            } else if (dir < 0) {
                return IN;
            } else {
                return OUT;
            }
        }
    }
}
