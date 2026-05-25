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

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.MessageUtil;
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
import buildcraft.builders.snapshot.EnumContainerContentsMode;
import buildcraft.builders.snapshot.EnumFluidHandlingMode;
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

    private static final Identifier ADVANCEMENT_PAVING_THE_WAY =
        Identifier.parse("buildcraftunofficial:paving_the_way");
    private static final Identifier ADVANCEMENT_START_OF_SOMETHING_BIG =
        Identifier.parse("buildcraftunofficial:start_of_something_big");
    /** Cumulative non-air cell count (summed across every base-pos completion) at which
     *  {@code start_of_something_big} fires. The advancement description calls this
     *  "an extra-large structure"; 1024 is a 32×32 floor or a 16×4×16 wall — comfortably
     *  above small-test builds (a 5×5×5 cube is 125) but reachable in normal play. */
    public static final long BIG_STRUCTURE_THRESHOLD = 1024L;

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private boolean canExcavate = true;
    private EnumFluidHandlingMode fluidMode = EnumFluidHandlingMode.NO_REPLACE;
    private EnumContainerContentsMode containerContentsMode = EnumContainerContentsMode.INCLUDE;

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
    /** Mirrors {@link #isDone} from the previous tick so a false→true transition is detectable
     *  exactly once per base-pos completion — without it, the advancement and counter logic
     *  would re-fire every tick the builder sits idle at the end of its work. */
    private boolean wasDoneLastTick = false;
    /** Running total of non-air cells the Builder has placed across every base-pos completion
     *  since it was placed. Persisted to NBT so a path-marker-fed Builder can accumulate the
     *  threshold across chunk reloads (or server restarts) mid-build. */
    private long bigStructureCellsBuilt = 0L;
    /** One-shot latches for the two advancements. Persisted to NBT so re-loading the chunk
     *  doesn't re-fire on every subsequent isDone tick. Each only flips to true once the
     *  grant call actually reaches a {@code ServerPlayer} — owner-offline calls return
     *  {@code false} and the latch stays armed for the next tick. */
    private boolean pavingTheWayGranted = false;
    private boolean startOfSomethingBigGranted = false;

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
            // from the server every 5 ticks via the block-entity update packet → getUpdateTag.
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
            boolean justCompletedBasePos = isDone && !wasDoneLastTick;
            wasDoneLastTick = isDone;
            if (isDone) {
                // Push one immediate sync so the client sees the completion and stops the robot.
                builder.onNetworkSync();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                // Advancement grants run on the false→true completion edge — i.e. exactly once
                // per base-pos finish, BEFORE we advance currentBasePosIndex (the paving check
                // needs to see the still-current index pointing at the just-finished position).
                if (justCompletedBasePos) {
                    tryGrantBuilderAdvancements();
                }
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
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }
    }

    /**
     * Predicate for {@code paving_the_way}: we just finished the LAST base position of a
     * multi-segment path. {@code currentBasePosIndex == basePoses.size() - 1} is the "this was
     * the final stamp" check; {@code path != null && path.size() >= 2} excludes Builders that
     * were placed without a path-marker chain (single-position builders shouldn't grant a
     * path-themed advancement).
     */
    boolean shouldGrantPavingTheWay() {
        return path != null
            && path.size() >= 2
            && !basePoses.isEmpty()
            && currentBasePosIndex == basePoses.size() - 1;
    }

    /**
     * Grant hook called on the false→true edge of {@link #isDone} for each base-pos completion.
     * Folds in two independent latches:
     *   - {@code paving_the_way}: gated on {@link #shouldGrantPavingTheWay()}.
     *   - {@code start_of_something_big}: accumulates {@code snapshot.countNonAirCells()} into
     *     {@link #bigStructureCellsBuilt}, fires when the running total crosses
     *     {@link #BIG_STRUCTURE_THRESHOLD}.
     * Each latch only flips to true once the grant call actually reaches a {@code ServerPlayer}.
     * Owner-offline calls return false and leave the latch armed, so the next online tick can
     * re-fire — matches the pattern used by {@code TileQuarry}'s diggy_diggy_hole grant.
     */
    private void tryGrantBuilderAdvancements() {
        if (level == null || level.isClientSide() || getOwner() == null) {
            return;
        }
        java.util.UUID ownerId = getOwner().id();
        if (!startOfSomethingBigGranted && snapshot != null) {
            bigStructureCellsBuilt += snapshot.countNonAirCells();
            if (bigStructureCellsBuilt >= BIG_STRUCTURE_THRESHOLD) {
                if (AdvancementUtil.unlockAdvancement(ownerId, level, ADVANCEMENT_START_OF_SOMETHING_BIG)) {
                    startOfSomethingBigGranted = true;
                }
            }
        }
        if (!pavingTheWayGranted && shouldGrantPavingTheWay()) {
            if (AdvancementUtil.unlockAdvancement(ownerId, level, ADVANCEMENT_PAVING_THE_WAY)) {
                pavingTheWayGranted = true;
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
                // The BuildingInfo constructor bakes required items with includeContents=true.
                // If this builder was saved/loaded with IGNORE, re-bake now so the resource panel
                // matches mode immediately — without it, the panel would show the chest-contents
                // cost until the player clicked the contents-mode button.
                if (containerContentsMode == EnumContainerContentsMode.IGNORE) {
                    blueprintBuildingInfo.refreshRequiredItemsForContentsMode(containerContentsMode);
                }
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
        output.putInt("fluidMode", fluidMode.ordinal());
        output.putInt("containerContentsMode", containerContentsMode.ordinal());
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
        // Persist the path-marker chain consumed at placement. Two reasons:
        //   1. Server-side chunk reload: without this, updateBasePoses() collapses to the single-
        //      position fallback and the Builder forgets its multi-segment route.
        //   2. Client-side rendering: BCBuildersEventDist#renderAllBuilders draws yellow stripes
        //      between consecutive path positions, but only sees the field via the periodic BE
        //      update packet — which goes through saveAdditional. Without serialization, path is
        //      null on the client and the laser render is dead code.
        if (path != null) {
            output.putInt("path_count", path.size());
            for (int i = 0; i < path.size(); i++) {
                BlockPos p = path.get(i);
                output.putInt("path_" + i + "_x", p.getX());
                output.putInt("path_" + i + "_y", p.getY());
                output.putInt("path_" + i + "_z", p.getZ());
            }
        }
        // Builder advancement state: progress toward start_of_something_big plus both latches.
        // wasDoneLastTick rides along so a reload mid-completion-edge doesn't re-fire grants
        // by treating the next sticky isDone tick as a fresh edge.
        output.putLong("bigStructureCellsBuilt", bigStructureCellsBuilt);
        output.putBoolean("pavingTheWayGranted", pavingTheWayGranted);
        output.putBoolean("startOfSomethingBigGranted", startOfSomethingBigGranted);
        output.putBoolean("wasDoneLastTick", wasDoneLastTick);
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
        // Absolute set, not addPower. ClientboundBlockEntityDataPacket re-runs loadAdditional on
        // the client every 5 ticks; the additive call would accumulate the server's snapshot
        // into the existing client value each time, climbing past capacity in seconds.
        battery.setStored(stored);
        canExcavate = input.getBooleanOr("canExcavate", true);
        fluidMode = EnumFluidHandlingMode.fromOrdinal(input.getIntOr("fluidMode", 0));
        containerContentsMode = EnumContainerContentsMode.fromOrdinal(input.getIntOr("containerContentsMode", 0));
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
        // Restore advancement state — defaults preserve a fresh-builder zero/false posture for
        // pre-fix saves that never had these keys, so existing worlds don't spuriously grant.
        bigStructureCellsBuilt = input.getLongOr("bigStructureCellsBuilt", 0L);
        pavingTheWayGranted = input.getBooleanOr("pavingTheWayGranted", false);
        startOfSomethingBigGranted = input.getBooleanOr("startOfSomethingBigGranted", false);
        wasDoneLastTick = input.getBooleanOr("wasDoneLastTick", false);
        // Restore the consumed path-marker chain. Counterpart to the writer above — required for
        // both server-side chunk-reload persistence and client-side path-laser rendering.
        int pathCount = input.getIntOr("path_count", 0);
        if (pathCount >= 2) {
            ImmutableList.Builder<BlockPos> rebuilt = ImmutableList.builder();
            for (int i = 0; i < pathCount; i++) {
                rebuilt.add(new BlockPos(
                    input.getIntOr("path_" + i + "_x", 0),
                    input.getIntOr("path_" + i + "_y", 0),
                    input.getIntOr("path_" + i + "_z", 0)
                ));
            }
            path = rebuilt.build();
        } else {
            path = null;
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
    public EnumFluidHandlingMode getFluidMode() {
        return fluidMode;
    }

    /**
     * Advance {@link #fluidMode} one step. Called from the Container on NET_FLUID_MODE_CLICK.
     * Dirties the chunk so the new ordinal survives save/load and invalidates any fluid-adjacent
     * check results so the builder re-evaluates whether those positions now need break or place
     * tasks under the new mode.
     */
    public void cycleFluidMode() {
        fluidMode = fluidMode.next();
        setChanged();
        SnapshotBuilder<?> b = getBuilder();
        if (b != null) {
            b.invalidateChecksForFluidPositions();
        }
    }

    @Override
    public EnumContainerContentsMode getContainerContentsMode() {
        return containerContentsMode;
    }

    /**
     * Advance {@link #containerContentsMode} one step. Called from the Container on
     * NET_CONTENTS_MODE_CLICK. Toggling between INCLUDE and IGNORE changes both the required-items
     * list (so the resource panel reflects the new cost) and the placed-block NBT (filled vs.
     * stripped), so we re-bake the active {@link Blueprint.BuildingInfo}'s per-position required
     * items in-place rather than tearing the snapshot down and re-loading it from disk. Without
     * the re-bake the resource panel would keep showing the old mode's contents list until the
     * blueprint was reloaded.
     */
    public void cycleContainerContentsMode() {
        containerContentsMode = containerContentsMode.next();
        setChanged();
        if (blueprintBuildingInfo != null) {
            blueprintBuildingInfo.refreshRequiredItemsForContentsMode(containerContentsMode);
        }
        SnapshotBuilder<?> b = getBuilder();
        if (b != null) {
            b.resourcesChanged();
            // BlueprintBuilder has an extra side-array (remainingDisplayRequiredBlocks) feeding the
            // GUI resource panel that won't refresh until check() naturally wraps around to each
            // position — re-bake it here so the panel snaps to the new mode immediately.
            if (b instanceof BlueprintBuilder bb) {
                bb.refreshDisplayForContentsMode();
            }
        }
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

    // Destruction-laser tier + drop routing ─────────────────────────────────────

    /**
     * Iron-pickaxe tier matches the {@code mining_well} ingredient that's nested in the Builder
     * recipe (Builder → Filler → Mining Well). {@code NEEDS_DIAMOND_TOOL} blocks (obsidian,
     * ancient debris, …) still destroy under this tool, they just don't drop anything — that
     * matches the user-visible "destroy hard blocks" fallback the Builder needs for CLEAR mode.
     */
    @Override
    public ItemStack getBreakingTool() {
        return new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
    }

    /**
     * Builder routing: drops go into {@link #invResources} via the same transactor pipes use, so
     * the broken blocks stack with player-supplied resources for the place phase. Anything that
     * doesn't fit spills into the world at the broken position — picking "spill at brokenPos"
     * over "drop at builderPos" because the broken block is where players intuitively expect any
     * uncaught items to appear. XP is spawned at the Builder block (parallel to Mining Well /
     * Quarry convention) — players collect it at the machine they placed.
     * <p>
     * Captured fluid: only the CLEAR fluid mode can produce a non-empty {@code capturedFluid}
     * here (NO_REPLACE skips fluid positions entirely; REPLACE handles them in the place phase
     * via waterlog / destroy, not the break path). Under CLEAR we absorb the source into the
     * Builder's tanks so the player gets the water/lava/oil back instead of it just vanishing.
     * Tank-full overflow is silently dropped — spilling the fluid back at brokenPos would
     * recreate exactly the block we just cleared, defeating CLEAR's purpose, and the tanks are
     * generous enough (4 × 8 buckets = 32) that overflow in practice means "you need to plumb
     * an output pipe."
     */
    @Override
    public void onBlockBroken(BlockPos brokenPos, java.util.List<ItemStack> drops, int xp,
            net.neoforged.neoforge.fluids.FluidStack capturedFluid) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = invResourcesTransactor.insert(stack.copy(), false, false);
            if (!remaining.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(serverLevel, brokenPos, remaining);
            }
        }
        if (xp > 0) {
            getBlockState().getBlock().popExperience(serverLevel, worldPosition, xp);
        }
        // Tank-absorb cleared fluid (CLEAR mode only). Defensive recheck on the mode here
        // even though the break queue only admits fluids under CLEAR — keeps the contract
        // local to this method so a future caller change can't accidentally tank-fill.
        if (!capturedFluid.isEmpty() && getFluidMode() == buildcraft.builders.snapshot.EnumFluidHandlingMode.CLEAR) {
            try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                    net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                tankManager.insert(
                        net.neoforged.neoforge.transfer.fluid.FluidResource.of(capturedFluid),
                        capturedFluid.getAmount(),
                        tx);
                tx.commit();
            }
        }
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
