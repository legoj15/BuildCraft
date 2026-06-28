/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.schematics.SchematicEntityContext;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerArchitectTable;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.SchematicEntityManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Snapshot.Header;
import buildcraft.builders.snapshot.Template;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;

public class TileArchitectTable extends TileBC_Neptune implements IDebuggable, MenuProvider {

    private static final net.minecraft.resources.Identifier ADVANCEMENT
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:architect");

    private EnumSnapshotType snapshotType = EnumSnapshotType.BLUEPRINT;
    public final Box box = new Box();
    public boolean markerBox = false;
    private BitSet templateScannedBlocks;
    private final List<ISchematicBlock> blueprintScannedPalette = new ArrayList<>();
    private int[] blueprintScannedData;
    private final List<ISchematicEntity> blueprintScannedEntities = new ArrayList<>();
    private boolean isValid = false;
    private boolean scanning = false;
    public String name = "<unnamed>";

    // Inline scan position tracking (replaces BoxIterator which is a stub)
    private int scanX, scanY, scanZ;
    private boolean scanInitialized = false;

    // Pipe-facing snapshot slots (slot 0 = input, slot 1 = output). Restored to the 1.12.2
    // ItemHandlerManager form so item pipes can feed a blank Blueprint/Template into the input
    // (INSERT) and pull the finished snapshot out of the output (EXTRACT); BCBuilders registers
    // ARCHITECT for Capabilities.Item.BLOCK so these surfaces are visible to adjacent pipes. The
    // input checker mirrors 1.12.2 (any ItemSnapshot), matching ContainerArchitectTable's GUI slot.
    public final ItemHandlerSimple invSnapshotIn = itemManager.addInvHandler(
        "in", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot,
        EnumAccess.INSERT, EnumPipePart.VALUES);
    public final ItemHandlerSimple invSnapshotOut = itemManager.addInvHandler(
        "out", 1,
        EnumAccess.EXTRACT, EnumPipePart.VALUES);

    // Scanning progress for GUI
    private int scanProgress = 0;
    private int scanTotal = 0;
    // Post-scan "drop" animation: when a scan finishes this counts down from 10, draining
    // scanProgress at maxPerTick per tick — matches the 1.12.2 deltaProgress.addDelta(size,
    // size+10, -1) closure flourish so the bar visibly relaxes instead of snapping to empty.
    private static final int DROP_TICKS = 10;
    private int dropCountdown = 0;

    // Positions scanned during the current server tick — drained at the end of tick() and
    // shipped in a single ArchitectScanPayload so the client can spawn digitizing cubes.
    private final List<BlockPos> scannedThisTick = new ArrayList<>();

    // Live preview of the current scan-area contents (shown in the GUI before a blueprint is
    // actually built). Regenerated lazily on request with a short TTL so repeated GUI queries
    // don't rescan every frame, but block changes still surface within ~2s.
    @Nullable private Blueprint cachedLivePreview;
    private long livePreviewGeneratedTick = Long.MIN_VALUE;
    private static final int LIVE_PREVIEW_TTL_TICKS = 40;
    private static final int LIVE_PREVIEW_MAX_VOLUME = 32 * 32 * 32;

    public TileArchitectTable(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.ARCHITECT.get(), pos, state);
    }

    // The snapshot in/out slots are real itemManager handlers, so the central addDrops path spills
    // them on a non-player removal (explosion / piston / command), matching playerWillDestroy.
    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cachedLivePreview = null;
        buildcraft.builders.BCBuildersEventDist.INSTANCE.invalidateArchitectTable(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.validateArchitectTable(this);
    }


    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) return;

        cachedLivePreview = null;

        BlockState blockState = level.getBlockState(worldPosition);
        Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos offsetPos = worldPosition.relative(facing.getOpposite());

        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumeBox volumeBox = volumeBoxes.getVolumeBoxAt(offsetPos);
        BlockEntity tile = level.getBlockEntity(offsetPos);

        if (volumeBox != null) {
            box.reset();
            box.setMin(volumeBox.box.min());
            box.setMax(volumeBox.box.max());
            isValid = true;
            volumeBox.locks.add(
                new Lock(
                    new Lock.Cause.CauseBlock(worldPosition, blockState.getBlock()),
                    new Lock.Target.TargetRemove(),
                    new Lock.Target.TargetResize(),
                    new Lock.Target.TargetUsedByMachine(
                        Lock.Target.TargetUsedByMachine.EnumType.STRIPES_READ
                    )
                )
            );
            volumeBoxes.markDirtyAndBroadcast();
        } else if (tile instanceof IAreaProvider provider) {
            box.reset();
            box.setMin(provider.min());
            box.setMax(provider.max());
            markerBox = true;
            isValid = true;
            provider.removeFromWorld();
        } else {
            isValid = false;
        }

        // Call parent to set the owner properly
        super.onPlacedBy(placer, stack);

        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide()) return;

        ItemStack stackIn = invSnapshotIn.getStackInSlot(0);
        if (!stackIn.isEmpty() && invSnapshotOut.getStackInSlot(0).isEmpty() && isValid) {
            if (!scanning) {
                if (stackIn.getItem() instanceof ItemSnapshot snapshotItem) {
                    snapshotType = snapshotItem.getSnapshotType();
                } else {
                    snapshotType = EnumSnapshotType.BLUEPRINT;
                }
                scanTotal = box.size().getX() * box.size().getY() * box.size().getZ();
                scanProgress = 0;
                scanning = true;
                scanInitialized = false;
                dropCountdown = 0;
            }
        } else {
            scanning = false;
            if (dropCountdown == 0) {
                scanProgress = 0;
                scanTotal = 0;
            }
        }

        if (scanning) {
            scanMultipleBlocks();
            if (!scanning) {
                if (snapshotType == EnumSnapshotType.BLUEPRINT) {
                    scanEntities();
                }
                finishScanning();
                dropCountdown = DROP_TICKS;
            }
        }

        if (dropCountdown > 0) {
            dropCountdown--;
            // Linear drop from scanTotal → 0 spread evenly across DROP_TICKS, so the bar
            // visibly drains regardless of scan volume. 1.12.2 dropped by a fixed 10 deltaInt
            // units, which was invisible on small scans.
            scanProgress = (int) ((long) scanTotal * dropCountdown / DROP_TICKS);
            if (dropCountdown == 0) {
                scanProgress = 0;
                scanTotal = 0;
            }
        }

        if (!scannedThisTick.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
                    serverLevel,
                    new net.minecraft.world.level.ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4),
                    new buildcraft.builders.snapshot.ArchitectScanPayload(new ArrayList<>(scannedThisTick))
            );
            scannedThisTick.clear();
        }
    }

    private void scanMultipleBlocks() {
        int maxPerTick = snapshotType.maxPerTick;
        for (int i = maxPerTick; i > 0; i--) {
            scanSingleBlock();
            if (!scanning) {
                break;
            }
        }
    }

    private void scanSingleBlock() {
        BlockPos size = box.size();
        if (!scanInitialized) {
            templateScannedBlocks = new BitSet(Snapshot.getDataSize(size));
            blueprintScannedData = new int[Snapshot.getDataSize(size)];
            scanX = 0;
            scanY = 0;
            scanZ = 0;
            scanInitialized = true;
        }

        BlockPos min = box.min();
        BlockPos worldScanPos = new BlockPos(min.getX() + scanX, min.getY() + scanY, min.getZ() + scanZ);
        BlockPos schematicPos = new BlockPos(scanX, scanY, scanZ);
        scannedThisTick.add(worldScanPos);

        if (snapshotType == EnumSnapshotType.TEMPLATE) {
            templateScannedBlocks.set(
                Snapshot.posToIndex(size, schematicPos),
                !level.isEmptyBlock(worldScanPos)
            );
        }
        if (snapshotType == EnumSnapshotType.BLUEPRINT) {
            ISchematicBlock schematicBlock = readSchematicBlock(worldScanPos);
            int index = blueprintScannedPalette.indexOf(schematicBlock);
            if (index == -1) {
                index = blueprintScannedPalette.size();
                blueprintScannedPalette.add(schematicBlock);
            }
            blueprintScannedData[Snapshot.posToIndex(size, schematicPos)] = index;
        }

        scanProgress++;

        // Advance position: iterate X, then Z, then Y (XZY order like 1.12.2)
        scanX++;
        if (scanX >= size.getX()) {
            scanX = 0;
            scanZ++;
            if (scanZ >= size.getZ()) {
                scanZ = 0;
                scanY++;
                if (scanY >= size.getY()) {
                    // Finished scanning
                    scanning = false;
                    scanInitialized = false;
                }
            }
        }
    }

    private ISchematicBlock readSchematicBlock(BlockPos worldScanPos) {
        return SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(
            level,
            box.min(),
            worldScanPos,
            level.getBlockState(worldScanPos),
            level.getBlockState(worldScanPos).getBlock()
        ));
    }

    /**
     * Produces a transient {@link Blueprint} snapshot of the current scan-area contents without
     * consuming an input item or persisting to {@link GlobalSavedDataSnapshots}. Used by the GUI
     * to show a 3D preview of what the Architect would scan right now, before a blueprint is
     * actually built.
     * <p>
     * Cached for {@link #LIVE_PREVIEW_TTL_TICKS} ticks so repeated requests during GUI
     * interaction are cheap. Returns {@code null} if the box is invalid or exceeds
     * {@link #LIVE_PREVIEW_MAX_VOLUME} to avoid stalling the server on pathological cases.
     */
    @Nullable
    public Blueprint getOrRefreshLivePreview() {
        if (level == null || level.isClientSide()) return null;
        if (!isValid || !box.isInitialized()) return null;

        BlockPos size = box.size();
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume <= 0 || volume > LIVE_PREVIEW_MAX_VOLUME) return null;

        long now = level.getGameTime();
        if (cachedLivePreview != null && now - livePreviewGeneratedTick < LIVE_PREVIEW_TTL_TICKS) {
            return cachedLivePreview;
        }

        BlockState thisState = level.getBlockState(worldPosition);
        if (thisState.getBlock() != BCBuildersBlocks.ARCHITECT.get()) return null;
        Direction facing = thisState.getValue(HorizontalDirectionalBlock.FACING);

        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();
        List<ISchematicBlock> palette = new ArrayList<>();
        int[] data = new int[Snapshot.getDataSize(size)];
        BlockPos min = box.min();

        // XZY iteration order matches scanSingleBlock() above.
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos worldScanPos = new BlockPos(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BlockPos schematicPos = new BlockPos(x, y, z);
                    ISchematicBlock sb = readSchematicBlock(worldScanPos);
                    int index = palette.indexOf(sb);
                    if (index == -1) {
                        index = palette.size();
                        palette.add(sb);
                    }
                    data[Snapshot.posToIndex(size, schematicPos)] = index;
                }
            }
        }

        Blueprint preview = new Blueprint();
        preview.size = size;
        preview.facing = facing;
        preview.offset = box.min().subtract(worldPosition.relative(facing.getOpposite()));
        preview.palette.addAll(palette);
        preview.data = data;
        // Entities intentionally skipped: BlueprintPipRenderer doesn't render them, so scanning
        // them here would just waste a level.getEntities() call per preview refresh.
        // Compute a content-addressed key so the client can detect when a refresh response
        // carries identical content and keep its existing Blueprint instance — that preserves
        // the identity hashcode BlueprintPipRenderer uses to avoid log spam, and keeps the
        // preview visible across periodic refreshes without a render-frame gap.
        preview.computeKey();

        cachedLivePreview = preview;
        livePreviewGeneratedTick = now;
        return preview;
    }

    private void scanEntities() {
        BlockPos min = box.min();
        BlockPos max = box.max();
        level.getEntities((Entity) null, new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, max.getY() + 1, max.getZ() + 1),
                entity -> true
            ).stream()
            .map(entity ->
                SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(
                    level,
                    box.min(),
                    entity
                ))
            )
            .filter(Objects::nonNull)
            .forEach(blueprintScannedEntities::add);
    }

    private void finishScanning() {
        BlockState thisState = level.getBlockState(worldPosition);
        if (thisState.getBlock() != BCBuildersBlocks.ARCHITECT.get()) {
            return;
        }

        Direction facing = thisState.getValue(HorizontalDirectionalBlock.FACING);
        Snapshot snapshot = Snapshot.create(snapshotType);
        snapshot.size = box.size();
        snapshot.facing = facing;
        snapshot.offset = box.min().subtract(worldPosition.relative(facing.getOpposite()));

        if (snapshot instanceof Template) {
            ((Template) snapshot).data = templateScannedBlocks;
        }
        if (snapshot instanceof Blueprint) {
            ((Blueprint) snapshot).palette.addAll(blueprintScannedPalette);
            ((Blueprint) snapshot).data = blueprintScannedData;
            ((Blueprint) snapshot).entities.addAll(blueprintScannedEntities);
        }
        snapshot.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(snapshot);

        // Consume one input item
        ItemStack stackIn = invSnapshotIn.getStackInSlot(0);
        stackIn.shrink(1);
        invSnapshotIn.setStackInSlot(0, stackIn.isEmpty() ? ItemStack.EMPTY : stackIn);

        // Produce output item
        ItemSnapshot usedItem = (snapshotType == EnumSnapshotType.BLUEPRINT)
            ? BCBuildersItems.BLUEPRINT_USED.get()
            : BCBuildersItems.TEMPLATE_USED.get();
        invSnapshotOut.setStackInSlot(0, usedItem.createUsedStack(
            new Header(
                snapshot.key,
                getOwner() != null ? GameProfileUtil.getId(getOwner()) : new java.util.UUID(0, 0),
                new Date(),
                name
            )
        ));

        // Reset scan state (scanProgress / scanTotal are kept intact so tick()'s drop animation
        // can drain them smoothly over DROP_TICKS ticks before zeroing).
        templateScannedBlocks = null;
        blueprintScannedData = null;
        blueprintScannedEntities.clear();
        if (getOwner() != null) {
            AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT);
            String paperCriterion = (snapshotType == EnumSnapshotType.BLUEPRINT)
                ? buildcraft.core.PaperAdvancement.WRITE_TO_BLUEPRINT
                : buildcraft.core.PaperAdvancement.WRITE_TO_TEMPLATE;
            AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level,
                buildcraft.core.PaperAdvancement.ID, paperCriterion);
        }
        setChanged();
        // Push the slot-state change to clients so the architect BER picks up the
        // scanning → done transition (full → half-lit red LED) immediately, rather
        // than waiting for the next chunk reload or GUI open.
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // NBT

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        if (box.isInitialized()) {
            output.putBoolean("box_initialized", true);
            BlockPos bMin = box.min();
            BlockPos bMax = box.max();
            output.putInt("box_minX", bMin.getX());
            output.putInt("box_minY", bMin.getY());
            output.putInt("box_minZ", bMin.getZ());
            output.putInt("box_maxX", bMax.getX());
            output.putInt("box_maxY", bMax.getY());
            output.putInt("box_maxZ", bMax.getZ());
        } else {
            output.putBoolean("box_initialized", false);
        }
        output.putBoolean("markerBox", markerBox);
        output.putBoolean("scanning", scanning);
        output.putInt("snapshotType", snapshotType.ordinal());
        output.putBoolean("isValid", isValid);
        output.putString("name", name);
        // Persist slot contents via the shared ItemHandlerManager (same path as the Filler /
        // Library / Replacer). Both snapshot slots round-trip under the "items" tag so a used
        // blueprint left in the output survives chunk unload.
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        if (input.getBooleanOr("box_initialized", false)) {
            int minX = input.getIntOr("box_minX", 0);
            int minY = input.getIntOr("box_minY", 0);
            int minZ = input.getIntOr("box_minZ", 0);
            int maxX = input.getIntOr("box_maxX", 0);
            int maxY = input.getIntOr("box_maxY", 0);
            int maxZ = input.getIntOr("box_maxZ", 0);
            box.reset();
            box.setMin(new BlockPos(minX, minY, minZ));
            box.setMax(new BlockPos(maxX, maxY, maxZ));
        }
        markerBox = input.getBooleanOr("markerBox", false);
        scanning = input.getBooleanOr("scanning", false);
        int stOrd = input.getIntOr("snapshotType", 0);
        EnumSnapshotType[] stValues = EnumSnapshotType.values();
        snapshotType = (stOrd >= 0 && stOrd < stValues.length) ? stValues[stOrd] : EnumSnapshotType.BLUEPRINT;
        isValid = input.getBooleanOr("isValid", false);
        name = input.getStringOr("name", "<unnamed>");
        // A scan interrupted by chunk unload/reload can't truly resume — the partial scan buffers
        // (palette + one int/bit per cell, megabytes for a large volume) aren't persisted, so the scan
        // restarts on the next tick (scanInitialized defaults false). scanTotal isn't persisted either,
        // so re-derive it here; otherwise it stays 0 and the progress bar reads empty for the whole
        // re-scan (most visible on the large volumes that are slow enough to be reloaded mid-scan).
        if (scanning && box.isInitialized()) {
            scanTotal = box.size().getX() * box.size().getY() * box.size().getZ();
            scanProgress = 0;
        }
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
        // Migrate the transient pre-fix plain-ItemStack format: earlier in the 26.1 port the two
        // snapshot slots were stored under top-level "invSnapshotIn" / "invSnapshotOut" keys. If a
        // world was saved in that window, fold those into the handlers so the blueprint isn't lost.
        if (invSnapshotIn.getStackInSlot(0).isEmpty()) {
            input.read("invSnapshotIn", ItemStack.CODEC)
                .ifPresent(s -> invSnapshotIn.setStackInSlot(0, s));
        }
        if (invSnapshotOut.getStackInSlot(0).isEmpty()) {
            input.read("invSnapshotOut", ItemStack.CODEC)
                .ifPresent(s -> invSnapshotOut.setStackInSlot(0, s));
        }
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("box:");
        left.add(" - min = " + box.min());
        left.add(" - max = " + box.max());
        left.add("scanning = " + scanning);
        left.add("isValid = " + isValid);
        left.add("scanProgress = " + scanProgress + "/" + scanTotal);
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.architect");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerArchitectTable(containerId, playerInv, this);
    }

    // Inventory access for the container

    public ItemStack getSnapshotIn() {
        return invSnapshotIn.getStackInSlot(0);
    }

    public void setSnapshotIn(ItemStack stack) {
        // setStackInSlot fires itemManager's callback -> setChanged(), so the write persists.
        invSnapshotIn.setStackInSlot(0, stack);
    }

    public ItemStack getSnapshotOut() {
        return invSnapshotOut.getStackInSlot(0);
    }

    public void setSnapshotOut(ItemStack stack) {
        invSnapshotOut.setStackInSlot(0, stack);
    }

    public boolean isScanning() {
        return scanning;
    }

    public int getScanProgress() {
        return scanProgress;
    }

    public int getScanTotal() {
        return scanTotal;
    }

    public boolean getIsValid() {
        return isValid;
    }
}