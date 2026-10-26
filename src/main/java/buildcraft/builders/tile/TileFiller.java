/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.tile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;
import buildcraft.api.core.IPlayerOwned;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.api.core.EnumPipePart;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.filler.FillPreviewCache;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.filler.FillerUtil;
import buildcraft.builders.snapshot.ITileForTemplateBuilder;
import buildcraft.builders.snapshot.SnapshotBuilder;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.TemplateBuilder;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.builders.BCBuildersEventDist;

public class TileFiller extends TileBC_Neptune
        implements IDebuggable, IFillerStatementContainer, IControllable, ITileForTemplateBuilder, IBCMenuProvider {

    public static final int INV_SIZE = 27;

    /**
     * How recent the builder's last real progress (power drawn into a task, a task finished) must be for the Filler to
     * count as building, in game ticks. Four sync periods (the tile pushes its data every 5 ticks while a build runs):
     * a Filler that is working has progressed within a tick or two of every sync, even one fed less than a single
     * pulse a second, so a longer gap means it is waiting for power, not building.
     */
    public static final int PROGRESS_WINDOW_TICKS = 20;

    public static final Identifier ADVANCEMENT_BUILDING_FOR_THE_FUTURE =
        Identifier.parse("buildcraftunofficial:building_for_the_future");

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    public MjBatteryReceiver getMjReceiver() {
        return mjReceiver;
    }

    private boolean canExcavate = true;
    public boolean inverted = false;
    /** Whether the Filler draws translucent cubes over the cells its pattern has yet to fill (see {@link FillPreviewCache}). */
    private boolean previewEnabled = false;
    private boolean finished = false;
    private byte lockedTicks = 0;
    private Mode mode = Mode.ON;
    /** Server only, not saved: whether {@link #migrateOrphanedBoxOnce} has had its one look this load. */
    private boolean orphanedBoxChecked;

    public final Box box = new Box();
    public boolean markerBox = true;

    public final FullStatement<IFillerPattern> patternStatement = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    public final ItemHandlerSimple invResources;

    private Template.BuildingInfo buildingInfo;
    /** What {@link #buildingInfo} was last built from, so a client copy can tell a changed pattern from a repeated sync. */
    private BuildKey builtFrom;
    /** Server only: the game time of the last tick the builder made progress ({@link SnapshotBuilder#madeProgress()}). */
    private long lastProgressTick = Long.MIN_VALUE / 2;
    /** Client only: the server's word, sent with every sync, that the builder progressed lately. */
    private boolean serverBuilding;
    /** Client only: the cells the preview draws, kept between frames. */
    public final FillPreviewCache previewCache = new FillPreviewCache();
    /** Client only: {@link #getRenderBounds()}, and what it was made from. */
    private AABB renderBounds;
    private boolean renderBoundsWithBox;
    private BlockPos renderBoundsMin, renderBoundsMax;
    public TemplateBuilder builder = new TemplateBuilder(this);

    /** The inputs of {@link #buildingInfo}: the pattern with its parameters, the invert flag and the box. */
    private record BuildKey(CompoundTag pattern, boolean inverted, @Nullable BlockPos boxMin, @Nullable BlockPos boxMax) {}

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    @SuppressWarnings("this-escape")
    public TileFiller(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.FILLER.get(), pos, state);
        invResources = itemManager.addInvHandler(
            "resources",
            INV_SIZE,
            (slot, stack) -> stack.getItem() instanceof net.minecraft.world.item.BlockItem,
            EnumAccess.INSERT,
            EnumPipePart.VALUES
        );
        invResources.setCallback((handler, slot, before, after) -> {
            this.setChanged();
            if (level != null && !level.isClientSide()) {
                if (builder != null) {
                    builder.resourcesChanged();
                }
                finished = false;
            }
        });
    }

    // ==================== Lifecycle (laser render tracking) ====================

    @Override
    public void setRemoved() {
        super.setRemoved();
        BCBuildersEventDist.INSTANCE.invalidateFiller(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        BCBuildersEventDist.INSTANCE.validateFiller(this);
    }

    /** The box this Filler owns goes with it (see {@link #followOwnedBox}). */
    @Override
    public void onBlockRemoved(Level level, BlockPos pos) {
        super.onBlockRemoved(level, pos);
        LevelSavedDataVolumeBoxes.get(level).removeOwnedBy(pos);
    }

    // ==================== Placement ====================

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Store owner (the inherited field; TileBC_Neptune saves it)
        if (placer instanceof Player player) {
            setOwner(player.getGameProfile());
        }

        BlockState blockState = level.getBlockState(worldPosition);
        Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos offsetPos = worldPosition.relative(facing.getOpposite());

        // Try volume box system first. A box another machine owns is that machine's area, not one to adopt.
        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumeBox volumeBox = volumeBoxes.getFreeVolumeBoxAt(offsetPos);
        BlockEntity tile = level.getBlockEntity(offsetPos);
        IAreaProvider area = tile instanceof IAreaProvider provider ? provider : volumeBoxes.getOneShotAreaAt(offsetPos);

        if (volumeBox != null) {
            box.reset();
            box.setMin(volumeBox.box.min());
            box.setMax(volumeBox.box.max());
            lockVolumeBox(volumeBoxes, volumeBox, blockState);
            markerBox = false;
        } else if (area != null) {
            box.reset();
            box.setMin(area.min());
            box.setMax(area.max());
            area.removeFromWorld();
            // The markers, or the floating box lifted off them, become a box this Filler owns, which the Volume
            // Wrench can resize. A Quarry just consumes such a box; this one is kept as the Filler's own, so no
            // second box is left behind. markerBox stays on: the renderer leaves the area to the box while there is
            // one (a copy of this tile has none).
            volumeBoxes.addOwnedBox(box.min(), box.max(), worldPosition, blockState.getBlock(),
                Lock.Target.TargetUsedByMachine.EnumType.STRIPES_WRITE);
        }

        updateBuildingInfo();
        setChanged();
        // Sync to client so the box data reaches the renderer (BER outline) — no chunk re-mesh needed.
        if (level != null && !level.isClientSide()) {
            markForRenderUpdate();
        }
    }

    /** Pins {@code volumeBox} to this Filler's block (no remove, no resize) unless a lock of this very block is already there. */
    private void lockVolumeBox(LevelSavedDataVolumeBoxes volumeBoxes, VolumeBox volumeBox, BlockState blockState) {
        boolean locked = volumeBox.locks.stream().anyMatch(lock ->
            lock.cause instanceof Lock.Cause.CauseBlock cause
                && worldPosition.equals(cause.pos) && cause.block == blockState.getBlock());
        if (locked) {
            return;
        }
        volumeBox.locks.add(
            new Lock(
                new Lock.Cause.CauseBlock(worldPosition, blockState.getBlock()),
                new Lock.Target.TargetRemove(),
                new Lock.Target.TargetResize(),
                new Lock.Target.TargetUsedByMachine(
                    Lock.Target.TargetUsedByMachine.EnumType.STRIPES_WRITE
                )
            )
        );
        volumeBoxes.markDirtyAndBroadcast();
    }

    /**
     * Server, the first tick after this Filler loads: a Filler that was driven by a Filler Planner (a Volume Box addon,
     * since removed) is bound to its box ({@code markerBox} false) yet never copied the box into itself, so it loads with
     * no box and does nothing while the box stays pinned by its lock. This takes the box over exactly as
     * {@link #onPlacedBy} does, together with the pattern and invert flag the planner held. Every other Filler (one
     * placed with nothing behind it, or one that already holds its box) is left alone, and it never looks again this
     * load. {@code volumeBoxes} is asked for only when there is something to look for.
     */
    void migrateOrphanedBoxOnce(java.util.function.Supplier<LevelSavedDataVolumeBoxes> volumeBoxes) {
        if (orphanedBoxChecked) {
            return;
        }
        orphanedBoxChecked = true;
        if (box.isInitialized() || markerBox) {
            return;
        }
        BlockState blockState = getBlockState();
        Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        LevelSavedDataVolumeBoxes savedBoxes = volumeBoxes.get();
        VolumeBox volumeBox = savedBoxes.getLockableVolumeBoxAt(worldPosition.relative(facing.getOpposite()));
        if (volumeBox == null) {
            return;
        }
        box.reset();
        box.setMin(volumeBox.box.min());
        box.setMax(volumeBox.box.max());
        lockVolumeBox(savedBoxes, volumeBox, blockState);
        // The planner's state belongs to the box and stays on it: every Filler backed onto the box built its pattern.
        VolumeBox.RetiredPlanner planner = volumeBox.retiredPlanner();
        if (planner != null) {
            if (!planner.pattern().isEmpty()) {
                patternStatement.readFromNbt(planner.pattern());
            }
            inverted = planner.inverted();
        }
        updateBuildingInfo();
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForRenderUpdate();
        }
    }

    // ==================== Building Info ====================

    private void updateBuildingInfo() {
        // Cancel existing builder before updating
        if (builder != null && getTemplateBuildingInfo() != null) {
            builder.cancel();
        }
        // Progress on the old plan says nothing about the new one; without this a pattern change on a starved
        // Filler would keep its new preview hidden until the old stamp ages out.
        lastProgressTick = Long.MIN_VALUE / 2;
        builtFrom = currentBuildKey();
        buildingInfo = hasBox() ? FillerUtil.createBuildingInfo(
            this,
            patternStatement,
            IntStream.range(0, patternStatement.maxParams)
                .mapToObj(patternStatement::get)
                .toArray(IStatementParameter[]::new),
            inverted
        ) : null;
        // Only update snapshot if we actually have valid building info
        if (getTemplateBuildingInfo() != null && builder != null) {
            builder.updateSnapshot();
        }
    }

    private BuildKey currentBuildKey() {
        boolean boxed = box.isInitialized();
        return new BuildKey(patternStatement.writeToNbt(), inverted, boxed ? box.min() : null, boxed ? box.max() : null);
    }

    // ==================== Tick ====================

    public void tick() {
        if (level == null) return;
        if (level.isClientSide()) {
            // Client: update pattern interactability and compute smooth robot visual interpolations
            patternStatement.canInteract = !isLocked();
            SnapshotBuilder<?> b = getBuilder();
            if (b != null) {
                b.clientTick();
            }
            return;
        }
        migrateOrphanedBoxOnce(() -> LevelSavedDataVolumeBoxes.get(level));
        battery.tick(level, worldPosition);
        lockedTicks--;
        if (lockedTicks < 0) {
            lockedTicks = 0;
        }
        if (followOwnedBox()) {
            return;
        }
        if (mode == Mode.OFF || isFinished()) {
            return;
        }
        SnapshotBuilder<?> b = getBuilder();
        if (b != null) {
            if (level.getGameTime() % 5 == 1) {
                b.onNetworkSync();
            }
            boolean done = b.tick();
            if (b.madeProgress()) {
                lastProgressTick = level.getGameTime();
            }
            if (!done) {
                // LOOP keeps ticking after a cycle completes; once it finds work again, re-arm
                // so the next completion syncs (and awards) again.
                finished = false;
            } else if (!finished) {
                // Gated on the false→true transition: LOOP's isFinished() stays false, so this
                // method keeps reaching here every tick once the builder has nothing left to do.
                if (mode == Mode.LOOP && getOwner() != null) {
                    AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_BUILDING_FOR_THE_FUTURE);
                }
                finished = true;
                // Clear stale render cache so the client receives empty task lists
                b.onNetworkSync();
                // Sync immediately so the client sees the finished state
                // (in ON mode next tick's isFinished() early return prevents further syncs).
                // Same no-re-mesh path as the periodic sendUpdateToTrackingPlayers below.
                markForRenderUpdate();
            }
            if (level.getGameTime() % 5 == 0) {
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }
    }

    // ==================== Owned box ====================

    /**
     * Keeps the working area in step with the volume box this Filler owns, if it owns one (it does when it took over
     * survival markers or a floating box lifted off them; a Filler from before boxes were owned has none and keeps its
     * area as it is). The box is
     * looked up by its owner position rather than by an id saved on the tile, so a copy of this tile's data (a
     * blueprint, a structure) can never point a second Filler at it.
     * <p>
     * {@link #box} stays the area the builder works on, mirrored from the owned box only once a drag is confirmed:
     * the builder's snapshot is laid out for one fixed area, so it must never see a box that changes under it. While
     * a drag is under way, the Filler waits; a confirmed new size restarts the job from scratch on the new area, and a
     * cancelled drag puts the old size back, so nothing changes.
     *
     * @return true while the box is being dragged: the Filler does no work this tick.
     */
    private boolean followOwnedBox() {
        VolumeBox owned = LevelSavedDataVolumeBoxes.get(level).getOwnedBy(worldPosition);
        if (owned == null) {
            return false;
        }
        if (owned.isEditing()) {
            return true;
        }
        if (!box.isInitialized() || !owned.box.min().equals(box.min()) || !owned.box.max().equals(box.max())) {
            box.reset();
            box.setMin(owned.box.min());
            box.setMax(owned.box.max());
            finished = false;
            updateBuildingInfo();
            setChanged();
            markForRenderUpdate();
        }
        return false;
    }

    // ==================== Statement Change ====================

    public void onStatementChange() {
        finished = false;
        updateBuildingInfo();
        // Everyone watching rebuilds from this change: a client's copy of the pattern, parameters and invert flag only
        // moves when the tile data reaches it, and the periodic sync stops while the Filler is idle, off or finished.
        if (level != null && !level.isClientSide()) {
            setChanged();
            markForRenderUpdate();
        }
    }

    // ==================== NBT ====================

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putLong("battery_mj", battery.getStored());
        output.putBoolean("canExcavate", canExcavate);
        output.putBoolean("inverted", inverted);
        output.putBoolean("showPreview", previewEnabled);
        output.putBoolean("building", isBuildingNow());
        output.putBoolean("finished", finished);
        output.putByte("lockedTicks", lockedTicks);
        output.putByte("mode", (byte) mode.ordinal());
        output.putBoolean("markerBox", markerBox);

        // Box
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

        // Pattern statement
        output.store("patternStatement", CompoundTag.CODEC, patternStatement.writeToNbt());

        // Resources
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());

        // Builder state for persistence + client rendering data
        if (builder != null) {
            output.store("builderState", net.minecraft.nbt.CompoundTag.CODEC, builder.serializeNBT());
            output.store("builderClientData", net.minecraft.nbt.CompoundTag.CODEC, builder.serializeClientNBT());
        }
        // The owner is saved by TileBC_Neptune.writeData (ownerUUID/ownerName).
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        // Battery: absolute set. Replaces the prior extractAll() + addPower() workaround that
        // existed because addPower lacks capacity clamping; setStored is the proper API.
        long stored = input.getLongOr("battery_mj", 0L);
        battery.setStored(stored);
        canExcavate = input.getBooleanOr("canExcavate", true);
        inverted = input.getBooleanOr("inverted", false);
        previewEnabled = input.getBooleanOr("showPreview", false);
        serverBuilding = input.getBooleanOr("building", false);
        finished = input.getBooleanOr("finished", false);
        lockedTicks = input.getByteOr("lockedTicks", (byte) 0);
        int modeOrdinal = input.getByteOr("mode", (byte) 0);
        Mode[] modes = Mode.values();
        mode = (modeOrdinal >= 0 && modeOrdinal < modes.length) ? modes[modeOrdinal] : Mode.ON;
        markerBox = input.getBooleanOr("markerBox", true);

        // Box
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

        // Pattern statement
        input.read("patternStatement", CompoundTag.CODEC).ifPresent(patternStatement::readFromNbt);

        // Resources
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);

        // Save client task lists BEFORE updateBuildingInfo() clears them via cancel().
        // These are needed for the max() merge in receiveServerTaskData to prevent backward animation jumps.
        java.util.List<SnapshotBuilder<ITileForTemplateBuilder>.BreakTask> savedBreak = new java.util.ArrayList<>();
        java.util.List<SnapshotBuilder<ITileForTemplateBuilder>.PlaceTask> savedPlace = new java.util.ArrayList<>();
        if (level != null && level.isClientSide() && builder != null) {
            savedBreak.addAll(builder.clientBreakTasks);
            savedPlace.addAll(builder.clientPlaceTasks);
        }

        // On the client during periodic network sync (level != null && level.isClientSide()),
        // only rebuild building info if it hasn't been initialized yet or what it was built from has changed
        // (pattern, parameters, invert, box) — otherwise the preview and the client builder would keep drawing the
        // old shape. Calling updateBuildingInfo() on EVERY sync (every 5 ticks) triggers cancel() which resets all
        // builder arrays, causing ghost items and item count flickering in the GUI.
        // On the server (disk load) or on initial client sync (buildingInfo == null), always rebuild.
        if (level == null || !level.isClientSide() || buildingInfo == null || !currentBuildKey().equals(builtFrom)) {
            updateBuildingInfo();
        }

        // Restore full builder state (for disk persistence) and client rendering tasks
        if (builder != null) {
            // Restore server-side builder progress (checkResults, breakTasks, etc.)
            input.read("builderState", net.minecraft.nbt.CompoundTag.CODEC).ifPresent(builder::deserializeNBT);

            // On the client, deserialize the server's task data and merge with saved client tasks
            // using max(client, server) power to prevent backward animation jumps.
            input.read("builderClientData", net.minecraft.nbt.CompoundTag.CODEC).ifPresent(tag -> {
                java.util.Queue<SnapshotBuilder<ITileForTemplateBuilder>.BreakTask> serverBreak = new java.util.ArrayDeque<>();
                java.util.Queue<SnapshotBuilder<ITileForTemplateBuilder>.PlaceTask> serverPlace = new java.util.ArrayDeque<>();
                buildcraft.lib.misc.NBTUtilBC.readCompoundList(tag.get("breakTasks"))
                    .map(cmp -> builder.new BreakTask(cmp))
                    .forEach(serverBreak::add);
                buildcraft.lib.misc.NBTUtilBC.readCompoundList(tag.get("placeTasks"))
                    .map(cmp -> builder.new PlaceTask(cmp))
                    .forEach(serverPlace::add);
                builder.receiveServerTaskData(serverBreak, serverPlace, savedBreak, savedPlace);
            });
        }

        // The owner itself is read by TileBC_Neptune.readData (ownerUUID/ownerName). Saves from before the Filler
        // shared that field kept the owner in a private "owner" child; read it when the standard keys are absent.
        if (owner == null) {
            owner = readLegacyOwner(input);
        }
    }

    /** The owner stored in the retired {@code owner} child (name/uuid), or null when there is none or its id is unusable. */
    @Nullable
    private static GameProfile readLegacyOwner(BCValueInput input) {
        Optional<BCValueInput> child = input.child("owner");
        if (child.isEmpty()) {
            return null;
        }
        String uuidStr = child.get().getStringOr("uuid", "");
        if (uuidStr.isEmpty()) {
            return null;
        }
        try {
            return new GameProfile(UUID.fromString(uuidStr), child.get().getStringOr("name", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== Network Sync ====================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveCustomOnly(registries);
        tag.remove("items");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== IDebuggable ====================

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("box = " + box);
        left.add("pattern = " + patternStatement.get());
        left.add("mode = " + mode);
        left.add("is_finished = " + finished);
        left.add("lockedTicks = " + lockedTicks);
        left.add("preview = " + previewEnabled);
        left.add("markerBox = " + markerBox);
        left.add("hasBox = " + hasBox());
        left.add("isValid = " + isValid());
        left.add("buildingInfo = " + (buildingInfo != null ? "present" : "null"));
        left.add("leftToBreak = " + getCountToBreak());
        left.add("leftToPlace = " + getCountToPlace());
    }

    // ==================== ITileForSnapshotBuilder ====================

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
        return isValid() ? builder : null;
    }

    // ==================== ITileForTemplateBuilder ====================

    @Override
    public Template.BuildingInfo getTemplateBuildingInfo() {
        return isValid() ? buildingInfo : null;
    }

    @Override
    public IItemTransactor getInvResources() {
        return invResources;
    }

    // ==================== Destruction-laser tier + drop routing ====================

    /**
     * Iron-pickaxe tier matches the {@code mining_well} ingredient in the Filler recipe.
     * {@code NEEDS_DIAMOND_TOOL} blocks (obsidian, ancient debris, …) still destroy under this
     * tool — they just don't drop anything, matching the Filler's "destroy hard blocks" fallback.
     */
    @Override
    public ItemStack getBreakingTool() {
        return new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
    }

    /**
     * Filler routing: drops eject into the world unless a non-pipe inventory block is placed
     * adjacent (any of the 6 faces), in which case insert there first. Pipes are explicitly
     * skipped — they shuttle items away from the Filler, defeating the point of "destruction
     * laser drops show up at your collection station next to the Filler." XP at the Filler block
     * (parallel to Mining Well / Builder convention). Captured fluid is ignored — the Filler
     * has no tanks, so a cleared water/lava source simply disappears.
     */
    @Override
    public void onBlockBroken(BlockPos brokenPos, List<ItemStack> drops, int xp,
            net.neoforged.neoforge.fluids.FluidStack capturedFluid) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = insertIntoAdjacentNonPipeInventory(serverLevel, stack.copy());
            if (!remaining.isEmpty()) {
                // Eject into the world at brokenPos (not the Filler) — matches the "you can
                // collect drops where the block used to be" intuition for sweeping fillers.
                net.minecraft.world.level.block.Block.popResource(serverLevel, brokenPos, remaining);
            }
        }
        if (xp > 0) {
            getBlockState().getBlock().popExperience(serverLevel, worldPosition, xp);
        }
    }

    /** Try each of the 6 adjacent positions in shuffled order; insert via the standard item
     *  handler capability but skip any block whose BE implements {@code IPipeHolder} so pipes
     *  don't intercept Filler drops. Returns the leftover that didn't fit anywhere. */
    private ItemStack insertIntoAdjacentNonPipeInventory(
            net.minecraft.server.level.ServerLevel serverLevel, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        java.util.List<Direction> faces = new java.util.ArrayList<>(java.util.List.of(Direction.values()));
        java.util.Collections.shuffle(faces);
        //? if >=1.21.10 {
        net.neoforged.neoforge.transfer.item.ItemResource resource =
                net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
        //?}
        int remaining = stack.getCount();
        for (Direction face : faces) {
            if (remaining <= 0) break;
            BlockPos adj = worldPosition.relative(face);
            BlockEntity adjBe = serverLevel.getBlockEntity(adj);
            // IPipeHolder skip: BC pipes shouldn't act as drop sinks for the Filler's destruction
            // laser per the user's "non-pipe inventory block" requirement. Other mods' transport
            // systems aren't recognized here — if a modded pipe registers Capabilities.Item.BLOCK
            // it would still siphon, but that's a known edge case we accept.
            if (adjBe instanceof buildcraft.api.transport.pipe.IPipeHolder) continue;
            //? if >=1.21.10 {
            var handler = serverLevel.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    adj, face.getOpposite());
            if (handler == null) continue;
            int inserted = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(
                    handler, resource, remaining, null);
            remaining -= inserted;
            //?} else {
            /*var handler = serverLevel.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    adj, face.getOpposite());
            if (handler == null) continue;
            ItemStack leftover = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                    handler, stack.copyWithCount(remaining), false);
            remaining = leftover.getCount();*/
            //?}
        }
        return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
    }

    // ==================== Accessors ====================

    public int getCountToPlace() {
        return builder != null ? builder.leftToPlace : 0;
    }

    public int getCountToBreak() {
        return builder != null ? builder.leftToBreak : 0;
    }

    public boolean isFinished() {
        return mode != Mode.LOOP && this.finished;
    }

    public boolean isLocked() {
        return lockedTicks > 0;
    }

    // ==================== IStatementContainer ====================

    @Override
    public BlockEntity getTile() {
        return this;
    }

    @Nullable
    @Override
    public BlockEntity getNeighbourTile(Direction side) {
        if (level == null) return null;
        return level.getBlockEntity(worldPosition.relative(side));
    }

    // ==================== IFillerStatementContainer ====================

    @Override
    public Level getFillerWorld() {
        return level;
    }

    @Override
    public boolean hasBox() {
        return box.isInitialized();
    }

    public boolean isValid() {
        if (!hasBox()) return false;
        return buildingInfo != null;
    }

    @Override
    public IBox getBox() {
        if (!hasBox()) {
            throw new IllegalStateException("Called getBox() when hasBox() returned false!");
        }
        return box;
    }

    @Override
    public void setPattern(IFillerPattern pattern, IStatementParameter[] params) {
        boolean changed = patternStatement.get() != pattern;
        if (!changed && params != null) {
            IStatementParameter[] currentParams = patternStatement.getParameters();
            for (int i = 0; i < params.length && i < currentParams.length; i++) {
                if (currentParams[i] != params[i]) {
                    changed = true;
                    break;
                }
            }
        }
        
        if (changed) {
            patternStatement.set(pattern, params);
            onStatementChange();
        }
        lockedTicks = 3;
    }

    // ==================== IControllable ====================

    @Override
    public Mode getControlMode() {
        return mode;
    }

    @Override
    public void setControlMode(Mode mode) {
        if (this.mode == Mode.OFF && mode != Mode.OFF) {
            finished = false;
        }
        // Entering LOOP from a non-LOOP state re-arms the builder so a Filler that finished
        // in ON mode kicks off another cycle when the player flips it to LOOP.
        if (mode == Mode.LOOP && this.mode != Mode.LOOP) {
            finished = false;
        }
        this.mode = mode;
        // Push the mode change to clients. The periodic sync inside tick() is gated on
        // mode != OFF && !isFinished(), so transitions *into* OFF (or into LOOP from
        // ON-finished) would otherwise leave the client with stale state — and the
        // BER would render the wrong LED pattern until the next chunk reload.
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForRenderUpdate();
        }
    }

    public boolean hasPower() {
        return battery.getStored() > 0;
    }

    // ==================== MenuProvider ====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.filler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerFiller(containerId, playerInv, this);
    }

    // ==================== Sync helpers for ContainerData ====================

    public boolean getCanExcavate() {
        return canExcavate;
    }

    public void setCanExcavate(boolean value) {
        this.canExcavate = value;
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    /** Turns the preview on or off for everyone near the Filler (server side; the change is pushed to clients). */
    public void setPreviewEnabled(boolean value) {
        if (previewEnabled == value) {
            return;
        }
        previewEnabled = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForRenderUpdate();
        }
    }

    /**
     * Client side: the area the Filler's renderer must stay alive for: the block, or with the preview on the block and
     * the whole box. Rebuilt only when the preview toggle or the box changes (compared by value, since every sync hands
     * over fresh position objects), because the renderer asks every frame.
     */
    public AABB getRenderBounds() {
        boolean withBox = previewEnabled && box.isInitialized();
        BlockPos min = withBox ? box.min() : null;
        BlockPos max = withBox ? box.max() : null;
        if (renderBounds == null || withBox != renderBoundsWithBox || !Objects.equals(min, renderBoundsMin)
                || !Objects.equals(max, renderBoundsMax)) {
            AABB block = new AABB(worldPosition);
            renderBounds = withBox ? block.minmax(box.getBoundingBox()) : block;
            renderBoundsWithBox = withBox;
            renderBoundsMin = min;
            renderBoundsMax = max;
        }
        return renderBounds;
    }

    /** Server side: whether the builder made progress within {@link #PROGRESS_WINDOW_TICKS}, as sent with every sync. */
    private boolean isBuildingNow() {
        if (level == null || mode == Mode.OFF || isFinished()) {
            return false;
        }
        long sinceProgress = level.getGameTime() - lastProgressTick;
        return sinceProgress >= 0 && sinceProgress <= PROGRESS_WINDOW_TICKS;
    }

    /**
     * Client side: true while this Filler is really placing or breaking blocks, which is what hides its preview. A
     * queued task is not enough, and neither is power in the battery:
     * <ul>
     * <li>A Filler switched OFF mid-build keeps the queue it last sent, since an OFF server stops syncing; and one with
     *     no power leaves its queue waiting for energy to spend.</li>
     * <li>The battery a client sees is what was left after the builder drew from it, so a Filler fed less than its draw
     *     limit (about 0.82 MJ a tick when empty) syncs 0 MJ every time while it builds.</li>
     * </ul>
     * So the server says it: {@code building} is set when the builder drew power or finished a task within
     * {@link #PROGRESS_WINDOW_TICKS}. The Filler must also be running (not OFF, not finished) with blocks still queued;
     * a gap between tasks is bridged by the grace period of {@link FillPreviewCache#IDLE_GRACE_TICKS}.
     */
    public boolean hasWorkInFlight() {
        return serverBuilding && mode != Mode.OFF && !isFinished()
            && builder != null && (!builder.clientBreakTasks.isEmpty() || !builder.clientPlaceTasks.isEmpty());
    }

    public boolean getFinished() {
        return finished;
    }

    public int getLockedTicks() {
        return lockedTicks;
    }

    public int getModeOrdinal() {
        return mode.ordinal();
    }
}
