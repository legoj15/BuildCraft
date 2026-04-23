/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IPathProvider;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.enums.EnumOptionalSnapshotType;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.BlueprintBuilder;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.ITileForBlueprintBuilder;
import buildcraft.builders.snapshot.ITileForTemplateBuilder;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.SnapshotBuilder;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.TemplateBuilder;

public class TileBuilder extends TileBC_Neptune
    implements IDebuggable, ITileForTemplateBuilder, ITileForBlueprintBuilder, MenuProvider {

    public static final int RESOURCE_SLOTS = 27;
    public static final int TANK_COUNT = 4;
    public static final int TANK_CAPACITY = 8 * 1000; // 8 buckets per tank

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private boolean canExcavate = true;

    /** Stores the real path - just a few block positions. */
    public List<BlockPos> path = null;
    /** Stores the real path plus all possible block positions inbetween. */
    private List<BlockPos> basePoses = new ArrayList<>();
    private int currentBasePosIndex = 0;
    private Snapshot snapshot = null;
    public EnumSnapshotType snapshotType = null;
    private Template.BuildingInfo templateBuildingInfo = null;
    private Blueprint.BuildingInfo blueprintBuildingInfo = null;
    @SuppressWarnings("WeakerAccess")
    public TemplateBuilder templateBuilder = new TemplateBuilder(this);
    @SuppressWarnings("WeakerAccess")
    public BlueprintBuilder blueprintBuilder = new BlueprintBuilder(this);
    private Box currentBox = new Box();
    private Rotation rotation = null;

    private boolean isDone = false;

    // Inventory. invSnapshot is the blueprint/template slot (used snapshots only).
    // invResources is a 27-slot grid (matches 1.12.2) that BlueprintBuilder pulls from via
    // getInvResources(). Plain ItemStack fields rather than a DeferredRegister-backed handler
    // because the 26.1 port hasn't landed a replacement for 1.12.2's ItemHandlerSimple yet and
    // TileArchitectTable uses the same pattern.
    private ItemStack invSnapshot = ItemStack.EMPTY;
    private final NonNullList<ItemStack> invResources = NonNullList.withSize(RESOURCE_SLOTS, ItemStack.EMPTY);

    // Fluid tanks: 4 slots, 8 buckets each, matching 1.12.2. Each tank is a single-slot
    // FluidStacksResourceHandler so WidgetFluidTank can bind to it directly in the GUI. The
    // combined tankManager below is what BlueprintBuilder uses for fluid-block placement.
    // Each insert/extract calls setChanged() so the chunk is marked dirty on mutation —
    // otherwise tank contents only persist by accident when the chunk saves for another reason.
    private final FluidStacksResourceHandler[] tanks = new FluidStacksResourceHandler[] {
        makeDirtyingTank(),
        makeDirtyingTank(),
        makeDirtyingTank(),
        makeDirtyingTank(),
    };

    private FluidStacksResourceHandler makeDirtyingTank() {
        return new FluidStacksResourceHandler(1, TANK_CAPACITY) {
            @Override
            public int insert(int slot, FluidResource resource, int amount, TransactionContext ctx) {
                int moved = super.insert(slot, resource, amount, ctx);
                if (moved > 0) setChanged();
                return moved;
            }

            @Override
            public int extract(int slot, FluidResource resource, int amount, TransactionContext ctx) {
                int moved = super.extract(slot, resource, amount, ctx);
                if (moved > 0) setChanged();
                return moved;
            }
        };
    }

    /** Combined read/write view over {@link #tanks} exposed via {@link #getTankManager()}. */
    private final ResourceHandler<FluidResource> tankManager = new ResourceHandler<>() {
        @Override
        public int size() {
            return tanks.length;
        }

        @Override
        public FluidResource getResource(int slot) {
            return slot >= 0 && slot < tanks.length ? tanks[slot].getResource(0) : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int slot) {
            return slot >= 0 && slot < tanks.length ? tanks[slot].getAmountAsLong(0) : 0;
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            return slot >= 0 && slot < tanks.length ? tanks[slot].getCapacityAsLong(0, resource) : 0;
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            return slot >= 0 && slot < tanks.length && tanks[slot].isValid(0, resource);
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext ctx) {
            return slot >= 0 && slot < tanks.length ? tanks[slot].insert(0, resource, amount, ctx) : 0;
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext ctx) {
            return slot >= 0 && slot < tanks.length ? tanks[slot].extract(0, resource, amount, ctx) : 0;
        }
    };

    /** Pipe-facing adapter: wraps {@link #invResources} as a NeoForge {@code ResourceHandler<ItemResource>}
     *  so pipes can push items into the Builder. Insert tries matching slots first then empty slots.
     *  Extract is intentionally disabled — 1.12.2 pipes also only inserted into the Builder's
     *  resource inventory; pulling finished items out doesn't make sense. */
    private final ResourceHandler<ItemResource> pipeItemHandler = new ResourceHandler<>() {
        @Override
        public int size() { return invResources.size(); }

        @Override
        public ItemResource getResource(int slot) {
            if (slot < 0 || slot >= invResources.size()) return ItemResource.EMPTY;
            ItemStack stack = invResources.get(slot);
            return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
        }

        @Override
        public long getAmountAsLong(int slot) {
            if (slot < 0 || slot >= invResources.size()) return 0;
            return invResources.get(slot).getCount();
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            if (slot < 0 || slot >= invResources.size()) return 0;
            // Standard 64-stack limit; honor the incoming resource's max stack size if specified
            // (rare, but some items have custom smaller limits).
            return resource == null || resource.isEmpty() ? 64 : resource.toStack(1).getMaxStackSize();
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return slot >= 0 && slot < invResources.size();
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext ctx) {
            if (slot < 0 || slot >= invResources.size() || resource == null || resource.isEmpty() || amount <= 0) return 0;
            ItemStack existing = invResources.get(slot);
            int maxStack = getCapacityAsInt(resource);
            if (existing.isEmpty()) {
                int moved = Math.min(amount, maxStack);
                ItemStack placed = resource.toStack(moved);
                // Transactions: snapshot/rollback would be more correct, but the snapshot builders
                // that drive the only extract path don't interleave with transactions, and the
                // pipe flow does its own bookkeeping. Commit immediately.
                invResources.set(slot, placed);
                onResourcesChanged();
                return moved;
            }
            if (!ItemStack.isSameItemSameComponents(existing, resource.toStack(1))) return 0;
            int space = Math.min(maxStack, existing.getMaxStackSize()) - existing.getCount();
            if (space <= 0) return 0;
            int moved = Math.min(space, amount);
            existing.grow(moved);
            onResourcesChanged();
            return moved;
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext ctx) {
            // Pipes can't pull items out of the Builder — matches 1.12.2 behavior.
            return 0;
        }

        private int getCapacityAsInt(ItemResource resource) {
            return (int) Math.min(Integer.MAX_VALUE, getCapacityAsLong(0, resource));
        }
    };

    /** Bridges the legacy {@link IItemTransactor} API the snapshot builders call into to the
     *  real {@link #invResources} list. Extract finds the first slot whose stack matches the
     *  filter and pulls up to {@code max}; insert merges into existing stacks first, then empty
     *  slots. Non-simulated mutations invalidate the builder's cached "has enough" results. */
    private final IItemTransactor invResourcesTransactor = new IItemTransactor() {
        @Override
        public ItemStack insert(ItemStack stack, boolean allOrNone, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Simulate first when allOrNone so we can bail before mutating anything.
            ItemStack remaining = stack.copy();
            NonNullList<ItemStack> scratch = simulate || allOrNone
                ? copyInventory()
                : invResources;

            // First pass: merge with matching stacks.
            for (int i = 0; i < scratch.size() && !remaining.isEmpty(); i++) {
                ItemStack slot = scratch.get(i);
                if (slot.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(slot, remaining)) continue;
                int max = Math.min(slot.getMaxStackSize(), remaining.getMaxStackSize());
                int space = max - slot.getCount();
                if (space <= 0) continue;
                int moved = Math.min(space, remaining.getCount());
                slot.grow(moved);
                remaining.shrink(moved);
                scratch.set(i, slot);
            }

            // Second pass: place into empty slots.
            for (int i = 0; i < scratch.size() && !remaining.isEmpty(); i++) {
                ItemStack slot = scratch.get(i);
                if (!slot.isEmpty()) continue;
                int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                ItemStack placed = remaining.copyWithCount(moved);
                scratch.set(i, placed);
                remaining.shrink(moved);
            }

            if (allOrNone && !remaining.isEmpty()) {
                return stack; // nothing fit; return the original untouched stack.
            }

            if (!simulate && (simulate || allOrNone)) {
                // allOrNone path worked against scratch; commit it now.
                for (int i = 0; i < scratch.size(); i++) {
                    invResources.set(i, scratch.get(i));
                }
            }

            if (!simulate) {
                onResourcesChanged();
            }
            return remaining;
        }

        @Override
        public ItemStack extract(IStackFilter filter, int min, int max, boolean simulate) {
            if (max <= 0) return ItemStack.EMPTY;

            // Always operate on a scratch copy so we can cleanly abort if we fail to meet
            // the minimum — the IItemTransactor contract says nothing is extracted in that
            // case, and we don't want to leave the inventory half-mutated.
            ItemStack accumulated = ItemStack.EMPTY;
            NonNullList<ItemStack> scratch = copyInventory();

            for (int i = 0; i < scratch.size() && accumulated.getCount() < max; i++) {
                ItemStack slot = scratch.get(i);
                if (slot.isEmpty()) continue;
                if (filter != null && !filter.matches(slot)) continue;

                if (accumulated.isEmpty()) {
                    int take = Math.min(max, slot.getCount());
                    accumulated = slot.copyWithCount(take);
                    slot.shrink(take);
                    scratch.set(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
                } else if (ItemStack.isSameItemSameComponents(accumulated, slot)) {
                    int want = max - accumulated.getCount();
                    int take = Math.min(want, slot.getCount());
                    accumulated.grow(take);
                    slot.shrink(take);
                    scratch.set(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
                }
            }

            if (accumulated.getCount() < min) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                for (int i = 0; i < scratch.size(); i++) {
                    invResources.set(i, scratch.get(i));
                }
                onResourcesChanged();
            }
            return accumulated;
        }
    };

    public TileBuilder(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.BUILDER.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.invalidateBuilder(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.validateBuilder(this);
    }

    public MjBatteryReceiver getMjReceiver() {
        return mjReceiver;
    }

    /** Exposes {@link #invResources} to NeoForge capabilities so pipes can push items in. */
    @Override
    public ResourceHandler<ItemResource> getItemHandler(Direction facing) {
        return pipeItemHandler;
    }

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) return;

        // Let TileBC_Neptune stash the owner GameProfile — TemplateBuilder's fake-player path
        // reads it via getOwner() during block placement, so without this the build silently
        // fails with a null-owner exception deep inside TemplateAPI.
        super.onPlacedBy(placer, stack);

        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockEntity inFront = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
        if (inFront instanceof IPathProvider provider) {
            ImmutableList<BlockPos> copiedPath = ImmutableList.copyOf(provider.getPath());
            if (copiedPath.size() >= 2) {
                path = copiedPath;
                provider.removeFromWorld();
            }
        }
        updateBasePoses();
        updateSnapshot(true);
    }

    public void tick() {
        if (level == null) return;

        if (level.isClientSide()) {
            // Client: run the visual interpolation for the robot cube + break/place lasers so
            // the robot flies between positions smoothly. The task lists themselves are pushed
            // from the server every 5 ticks via level.sendBlockUpdated → getUpdateTag.
            SnapshotBuilder<?> b = getBuilder();
            if (b != null) {
                b.clientTick();
            }
            return;
        }

        // Lazy server-side snapshot resolution. loadAdditional runs before setLevel during
        // chunk load, so the GlobalSavedDataSnapshots lookup inside loadAdditional is usually a
        // no-op. Retrying here on the first tick (level is definitely set) is the cheapest way
        // to recover without touching the BE lifecycle callbacks.
        if (snapshot == null && !invSnapshot.isEmpty() && invSnapshot.getItem() instanceof ItemSnapshot) {
            Snapshot.Header header = ItemSnapshot.getHeader(invSnapshot);
            if (header != null) {
                Snapshot resolved = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
                if (resolved != null) {
                    snapshot = resolved;
                    snapshotType = resolved.getType();
                    if (basePoses.isEmpty()) {
                        updateBasePoses();
                    }
                    updateSnapshot(false);
                }
            }
        }

        battery.tick(level, worldPosition);
        SnapshotBuilder<?> builder = getBuilder();
        // getBuildingInfo() guards against a stale invSnapshot whose underlying snapshot file
        // has gone missing on disk: getBuilder() is driven off snapshotType, which may be
        // BLUEPRINT while blueprintBuildingInfo is still null because updateSnapshot(...)
        // never got a real Snapshot to build against. Without this check BlueprintBuilder.tick
        // NPEs immediately on the first server tick.
        if (builder != null && getBuildingInfo() != null) {
            // Every 5 ticks: freeze the client-visible task cache so the next serializeClientNBT
            // captures a coherent snapshot. Matches TileFiller's cadence.
            if (level.getGameTime() % 5 == 1) {
                builder.onNetworkSync();
            }
            isDone = builder.tick();
            if (isDone) {
                // Push one immediate sync so the client sees the completion and stops the robot.
                builder.onNetworkSync();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                if (currentBasePosIndex < basePoses.size() - 1) {
                    currentBasePosIndex++;
                    if (currentBasePosIndex >= basePoses.size()) {
                        currentBasePosIndex = basePoses.size() - 1;
                    }
                    updateSnapshot(true);
                }
            }
            // Periodic sync. Re-rendering the tile entity's NBT every 5 ticks is cheap; the BE
            // update packet only goes to players with the chunk loaded, so the network cost is
            // bounded by player proximity.
            if (level.getGameTime() % 5 == 0) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void updateSnapshot(boolean canGetFacing) {
        Optional.ofNullable(getBuilder()).ifPresent(SnapshotBuilder::cancel);
        if (snapshot != null && getCurrentBasePos() != null) {
            snapshotType = snapshot.getType();
            if (canGetFacing) {
                rotation = Arrays.stream(Rotation.values())
                    .filter(r -> r.rotate(snapshot.facing) == getBlockState().getValue(HorizontalDirectionalBlock.FACING))
                    .findFirst().orElse(null);
            }
            if (snapshot.getType() == EnumSnapshotType.TEMPLATE) {
                templateBuildingInfo = ((Template) snapshot).new BuildingInfo(getCurrentBasePos(), rotation);
            }
            if (snapshot.getType() == EnumSnapshotType.BLUEPRINT) {
                blueprintBuildingInfo = ((Blueprint) snapshot).new BuildingInfo(getCurrentBasePos(), rotation);
            }
            currentBox = Optional.ofNullable(getBuildingInfo()).map(buildingInfo -> buildingInfo.box).orElse(null);
            Optional.ofNullable(getBuilder()).ifPresent(SnapshotBuilder::updateSnapshot);
        } else {
            snapshotType = null;
            rotation = null;
            templateBuildingInfo = null;
            blueprintBuildingInfo = null;
            currentBox = null;
        }
        if (currentBox == null) {
            currentBox = new Box();
        }
        syncBlockStateToSnapshot();
    }

    private void updateBasePoses() {
        basePoses.clear();
        if (path != null) {
            int max = path.size() - 1;
            basePoses.add(path.get(0));
            for (int i = 1; i <= max; i++) {
                basePoses.addAll(PositionUtil.getAllOnPath(path.get(i - 1), path.get(i)));
            }
        } else {
            basePoses.add(worldPosition.relative(
                getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite()));
        }
    }

    private BlockPos getCurrentBasePos() {
        return currentBasePosIndex < basePoses.size() ? basePoses.get(currentBasePosIndex) : null;
    }

    /** Called when the snapshot slot changes. */
    public void onSnapshotSlotChanged(ItemStack newStack) {
        if (level == null || level.isClientSide()) return;
        currentBasePosIndex = 0;
        snapshot = null;
        if (newStack.getItem() instanceof ItemSnapshot) {
            Snapshot.Header header = ItemSnapshot.getHeader(newStack);
            if (header != null) {
                Snapshot newSnapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
                if (newSnapshot != null) {
                    snapshot = newSnapshot;
                }
            }
        }
        if (basePoses.isEmpty()) {
            updateBasePoses();
        }
        updateSnapshot(true);
    }

    /** Pushes the current {@link #snapshotType} into the {@link BlockBuilder#SNAPSHOT_TYPE}
     *  blockstate property so the door submodel (empty/template/blueprint) matches what's
     *  actually loaded. Without this the front face always shows the "open cavity" texture. */
    private void syncBlockStateToSnapshot() {
        if (level == null || level.isClientSide()) return;
        BlockState cur = getBlockState();
        if (!cur.hasProperty(BlockBuilder.SNAPSHOT_TYPE)) return;
        EnumOptionalSnapshotType desired = EnumOptionalSnapshotType.fromNullable(snapshotType);
        if (cur.getValue(BlockBuilder.SNAPSHOT_TYPE) != desired) {
            level.setBlock(worldPosition, cur.setValue(BlockBuilder.SNAPSHOT_TYPE, desired), 3);
        }
    }

    /** Called by the container/transactor after any mutation to {@link #invResources} so the
     *  active builder invalidates its "has enough to place" cache.
     *  <p>Client-side is a no-op: the builder's {@code requiredCache} only exists once
     *  {@link SnapshotBuilder#updateSnapshot()} ran, which requires a loaded snapshot — and on
     *  the client the full snapshot never materialises (only {@code snapshotType} is synced
     *  for rendering purposes). Calling {@code resourcesChanged()} there hits an
     *  {@code Arrays.fill(null, …)} NPE that crashes the client during
     *  {@code ClientboundContainerSetContentPacket} handling. */
    public void onResourcesChanged() {
        setChanged();
        if (level != null && level.isClientSide()) return;
        Optional.ofNullable(getBuilder()).ifPresent(SnapshotBuilder::resourcesChanged);
    }

    // Snapshot slot accessors — used by ContainerBuilder's BuilderContainer wrapper.
    public ItemStack getSnapshot() {
        return invSnapshot;
    }

    public void setSnapshot(ItemStack stack) {
        invSnapshot = stack;
        onSnapshotSlotChanged(stack);
        setChanged();
    }

    public ItemStack getResource(int slot) {
        return slot >= 0 && slot < invResources.size() ? invResources.get(slot) : ItemStack.EMPTY;
    }

    public void setResource(int slot, ItemStack stack) {
        if (slot < 0 || slot >= invResources.size()) return;
        invResources.set(slot, stack);
        onResourcesChanged();
    }

    public FluidStacksResourceHandler getTank(int i) {
        return (i >= 0 && i < tanks.length) ? tanks[i] : null;
    }

    private NonNullList<ItemStack> copyInventory() {
        NonNullList<ItemStack> copy = NonNullList.withSize(invResources.size(), ItemStack.EMPTY);
        for (int i = 0; i < invResources.size(); i++) {
            copy.set(i, invResources.get(i).copy());
        }
        return copy;
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("battery_mj", battery.getStored());
        output.putBoolean("canExcavate", canExcavate);
        output.putInt("currentBasePosIndex", currentBasePosIndex);
        if (rotation != null) {
            output.putInt("rotation", rotation.ordinal());
        }
        // Box
        if (currentBox.isInitialized()) {
            output.putBoolean("box_initialized", true);
            BlockPos bMin = currentBox.min();
            BlockPos bMax = currentBox.max();
            output.putInt("box_minX", bMin.getX());
            output.putInt("box_minY", bMin.getY());
            output.putInt("box_minZ", bMin.getZ());
            output.putInt("box_maxX", bMax.getX());
            output.putInt("box_maxY", bMax.getY());
            output.putInt("box_maxZ", bMax.getZ());
        } else {
            output.putBoolean("box_initialized", false);
        }
        // Inventory
        if (!invSnapshot.isEmpty()) {
            output.store("invSnapshot", ItemStack.CODEC, invSnapshot);
        }
        for (int i = 0; i < invResources.size(); i++) {
            ItemStack stack = invResources.get(i);
            if (!stack.isEmpty()) {
                output.store("invRes_" + i, ItemStack.CODEC, stack);
            }
        }
        // Tanks: store fluid id + amount per tank (same shape as TileEngineIron_BC8).
        for (int i = 0; i < tanks.length; i++) {
            FluidResource res = tanks[i].getResource(0);
            if (!res.isEmpty()) {
                Identifier id = BuiltInRegistries.FLUID.getKey(res.getFluid());
                if (id != null) {
                    output.putString("tank_" + i + "_fluid", id.toString());
                    output.putInt("tank_" + i + "_amount", (int) tanks[i].getAmountAsLong(0));
                }
            }
        }
        // Persist snapshotType independently so the client can route loadClientNBT to the right
        // builder (template vs blueprint) without needing GlobalSavedDataSnapshots to cough up
        // the full snapshot — which it can't, on the client side.
        if (snapshotType != null) {
            output.putInt("snapshotType", snapshotType.ordinal());
        }
        // Builder progress: serialize the active builder's server-side state (for disk reload)
        // AND a client-facing snapshot of the current break/place queues (for BE update packets
        // driving the robot + throwing-animation render). Same shape as TileFiller.
        SnapshotBuilder<?> activeBuilder = getBuilder();
        if (activeBuilder != null) {
            output.store("builderState", CompoundTag.CODEC, activeBuilder.serializeNBT());
            output.store("builderClientData", CompoundTag.CODEC, activeBuilder.serializeClientNBT());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long stored = input.getLongOr("battery_mj", 0L);
        battery.addPower(stored, false);
        canExcavate = input.getBooleanOr("canExcavate", true);
        currentBasePosIndex = input.getIntOr("currentBasePosIndex", 0);
        int rotOrdinal = input.getIntOr("rotation", -1);
        if (rotOrdinal >= 0 && rotOrdinal < Rotation.values().length) {
            rotation = Rotation.values()[rotOrdinal];
        }
        // Box
        if (input.getBooleanOr("box_initialized", false)) {
            int minX = input.getIntOr("box_minX", 0);
            int minY = input.getIntOr("box_minY", 0);
            int minZ = input.getIntOr("box_minZ", 0);
            int maxX = input.getIntOr("box_maxX", 0);
            int maxY = input.getIntOr("box_maxY", 0);
            int maxZ = input.getIntOr("box_maxZ", 0);
            currentBox.reset();
            currentBox.setMin(new BlockPos(minX, minY, minZ));
            currentBox.setMax(new BlockPos(maxX, maxY, maxZ));
        }
        // Inventory
        invSnapshot = input.read("invSnapshot", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        for (int i = 0; i < invResources.size(); i++) {
            invResources.set(i, input.read("invRes_" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }
        // Tanks. loadAdditional is re-invoked every 5 ticks on the client via the periodic BE
        // update packet, so we MUST drain first before inserting — otherwise each sync adds
        // another copy of the fluid, making the client tanks "count up" until they cap.
        for (int i = 0; i < tanks.length; i++) {
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource existing = tanks[i].getResource(0);
                if (!existing.isEmpty()) {
                    tanks[i].extract(0, existing, Integer.MAX_VALUE, tx);
                }
                String fluidId = input.getStringOr("tank_" + i + "_fluid", "");
                if (!fluidId.isEmpty()) {
                    Identifier id = Identifier.tryParse(fluidId);
                    if (id != null) {
                        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
                        if (fluid != null && fluid != Fluids.EMPTY) {
                            int amount = input.getIntOr("tank_" + i + "_amount", 0);
                            if (amount > 0) {
                                tanks[i].insert(0, FluidResource.of(fluid), amount, tx);
                            }
                        }
                    }
                }
                tx.commit();
            }
        }
        // Pull snapshotType from NBT on the CLIENT only. On client the full snapshot never
        // loads (GlobalSavedDataSnapshots is empty), so NBT is the only path to knowing which
        // builder to route loadClientNBT into. On the SERVER we intentionally leave snapshotType
        // null here — loadAdditional runs before setLevel during chunk load, so the
        // GlobalSavedDataSnapshots lookup would fail anyway, and pre-setting snapshotType from
        // NBT would make getBuilder() return a builder whose buildingInfo is still null, which
        // crashes BlueprintBuilder.tick on the first server tick. We retry the snapshot
        // resolution lazily in tick() once level is known-set.
        if (level != null && level.isClientSide()) {
            int stOrdinal = input.getIntOr("snapshotType", -1);
            if (stOrdinal >= 0 && stOrdinal < EnumSnapshotType.values().length) {
                snapshotType = EnumSnapshotType.values()[stOrdinal];
            }
        }
        // On the client this method is called again every 5 ticks via the periodic
        // sendBlockUpdated sync. Capture the client-side task queues BEFORE updateSnapshot(...)
        // runs — it calls cancel() which clears them — so receiveServerTaskData can merge with
        // max(client, server) power afterwards and the animation never jumps backwards.
        List<SnapshotBuilder<?>.BreakTask> savedBreak = new ArrayList<>();
        List<SnapshotBuilder<?>.PlaceTask> savedPlace = new ArrayList<>();
        if (level != null && level.isClientSide()) {
            SnapshotBuilder<?> prev = getBuilder();
            if (prev != null) {
                savedBreak.addAll(prev.clientBreakTasks);
                savedPlace.addAll(prev.clientPlaceTasks);
            }
        }

        // Reload snapshot if invSnapshot carries a header. Placement/path state (basePoses,
        // rotation, currentBox) is already restored above, so this call re-materializes the
        // Snapshot object from GlobalSavedDataSnapshots without resetting the base-pos index.
        if (!invSnapshot.isEmpty() && invSnapshot.getItem() instanceof ItemSnapshot) {
            Snapshot.Header header = ItemSnapshot.getHeader(invSnapshot);
            if (header != null && level != null) {
                Snapshot newSnapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
                if (newSnapshot != null) {
                    snapshot = newSnapshot;
                    snapshotType = newSnapshot.getType();
                    updateBasePoses();
                    updateSnapshot(false);
                }
            }
        }

        // Now that the active builder exists and its building info is set up, apply the server
        // state and the client task data. Server disk path: restore full builder progress.
        // Client network-sync path: merge server tasks with the saved client queues.
        SnapshotBuilder<?> active = getBuilder();
        if (active != null) {
            input.read("builderState", CompoundTag.CODEC).ifPresent(active::deserializeNBT);
            input.read("builderClientData", CompoundTag.CODEC).ifPresent(tag ->
                applyBuilderClientData(active, tag, savedBreak, savedPlace)
            );
        }
    }

    /** Wrapper around {@link SnapshotBuilder#loadClientNBT} whose only job is to swallow the
     *  raw-types ceremony needed because the {@code savedBreak}/{@code savedPlace} lists were
     *  captured with a wildcard builder reference before we knew which concrete builder would
     *  apply the data. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyBuilderClientData(SnapshotBuilder active, CompoundTag tag,
            List savedBreak, List savedPlace) {
        active.loadClientNBT(tag, savedBreak, savedPlace);
    }

    // Rendering

    public Box getBox() {
        return currentBox;
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("basePoses = " + (basePoses == null ? "null" : basePoses.size()));
        left.add("currentBasePosIndex = " + currentBasePosIndex);
        left.add("isDone = " + isDone);
        left.add("snapshotType = " + snapshotType);
    }

    // ITileForSnapshotBuilder

    @Override
    public Level getWorldBC() {
        return level;
    }

    @Override
    public MjBattery getBattery() {
        return battery;
    }

    @Override
    public BlockPos getBuilderPos() {
        return worldPosition;
    }

    @Override
    public boolean canExcavate() {
        return canExcavate;
    }

    @Override
    public SnapshotBuilder<?> getBuilder() {
        if (snapshotType == EnumSnapshotType.TEMPLATE) {
            return templateBuilder;
        }
        if (snapshotType == EnumSnapshotType.BLUEPRINT) {
            return blueprintBuilder;
        }
        return null;
    }

    private Snapshot.BuildingInfo getBuildingInfo() {
        if (snapshotType == EnumSnapshotType.TEMPLATE) {
            return templateBuildingInfo;
        }
        if (snapshotType == EnumSnapshotType.BLUEPRINT) {
            return blueprintBuildingInfo;
        }
        return null;
    }

    // ITileForTemplateBuilder

    @Override
    public Template.BuildingInfo getTemplateBuildingInfo() {
        return templateBuildingInfo;
    }

    // ITileForBlueprintBuilder

    @Override
    public Blueprint.BuildingInfo getBlueprintBuildingInfo() {
        return blueprintBuildingInfo;
    }

    @Override
    public IItemTransactor getInvResources() {
        return invResourcesTransactor;
    }

    @Override
    public ResourceHandler<FluidResource> getTankManager() {
        return tankManager;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.builder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerBuilder(containerId, playerInv, this);
    }
}
