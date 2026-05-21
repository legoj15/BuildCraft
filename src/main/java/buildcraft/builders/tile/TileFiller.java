/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.tile;

import java.util.List;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.api.core.EnumPipePart;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.ContainerFiller;
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
        implements IDebuggable, IFillerStatementContainer, IControllable, ITileForTemplateBuilder, MenuProvider {

    public static final int INV_SIZE = 27;

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    public MjBatteryReceiver getMjReceiver() {
        return mjReceiver;
    }

    private boolean canExcavate = true;
    public boolean inverted = false;
    private boolean finished = false;
    private byte lockedTicks = 0;
    private Mode mode = Mode.ON;

    public final Box box = new Box();
    public AddonFillerPlanner addon;
    public boolean markerBox = true;

    public final FullStatement<IFillerPattern> patternStatement = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    public final ItemHandlerSimple invResources;

    private Template.BuildingInfo buildingInfo;
    public TemplateBuilder builder = new TemplateBuilder(this);
    @Nullable
    private GameProfile owner;

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

    // ==================== Placement ====================

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Store owner
        if (placer instanceof Player player) {
            owner = player.getGameProfile();
        }

        BlockState blockState = level.getBlockState(worldPosition);
        Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos offsetPos = worldPosition.relative(facing.getOpposite());

        // Try volume box system first
        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumeBox volumeBox = volumeBoxes.getVolumeBoxAt(offsetPos);
        BlockEntity tile = level.getBlockEntity(offsetPos);

        if (volumeBox != null) {
            // Check if the volume box has a filler planner addon
            addon = (AddonFillerPlanner) volumeBox.addons
                .values()
                .stream()
                .filter(AddonFillerPlanner.class::isInstance)
                .findFirst()
                .orElse(null);

            if (addon != null) {
                volumeBox.locks.add(
                    new Lock(
                        new Lock.Cause.CauseBlock(worldPosition, blockState.getBlock()),
                        new Lock.Target.TargetAddon(addon.getSlot()),
                        new Lock.Target.TargetRemove(),
                        new Lock.Target.TargetResize(),
                        new Lock.Target.TargetUsedByMachine(
                            Lock.Target.TargetUsedByMachine.EnumType.STRIPES_WRITE
                        )
                    )
                );
                volumeBoxes.markDirtyAndBroadcast();
                addon.updateBuildingInfo();
                markerBox = false;
            } else {
                box.reset();
                box.setMin(volumeBox.box.min());
                box.setMax(volumeBox.box.max());
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
                markerBox = false;
            }
        } else if (tile instanceof IAreaProvider provider) {
            box.reset();
            box.setMin(provider.min());
            box.setMax(provider.max());
            provider.removeFromWorld();
        }

        updateBuildingInfo();
        setChanged();
        // Sync to client so the box data reaches the renderer
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ==================== Building Info ====================

    private void updateBuildingInfo() {
        // Cancel existing builder before updating
        if (builder != null && getTemplateBuildingInfo() != null) {
            builder.cancel();
        }
        buildingInfo = (hasBox() && addon == null) ? FillerUtil.createBuildingInfo(
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
        battery.tick(level, worldPosition);
        lockedTicks--;
        if (lockedTicks < 0) {
            lockedTicks = 0;
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
            if (done) {
                finished = true;
                // Clear stale render cache so the client receives empty task lists
                b.onNetworkSync();
                // Sync immediately so the client sees the finished state
                // (next tick's isFinished() early return will prevent further syncs)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            if (level.getGameTime() % 5 == 0) {
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }
    }

    // ==================== Statement Change ====================

    public void onStatementChange() {
        finished = false;
        updateBuildingInfo();
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("battery_mj", battery.getStored());
        output.putBoolean("canExcavate", canExcavate);
        output.putBoolean("inverted", inverted);
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

        // Owner
        if (owner != null) {
            ValueOutput ownerChild = output.child("owner");
            ownerChild.putString("name", owner.name() != null ? owner.name() : "");
            ownerChild.putString("uuid", owner.id() != null ? owner.id().toString() : "");
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Battery: absolute set. Replaces the prior extractAll() + addPower() workaround that
        // existed because addPower lacks capacity clamping; setStored is the proper API.
        long stored = input.getLongOr("battery_mj", 0L);
        battery.setStored(stored);
        canExcavate = input.getBooleanOr("canExcavate", true);
        inverted = input.getBooleanOr("inverted", false);
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
        // only rebuild building info if it hasn't been initialized yet.
        // Calling updateBuildingInfo() every 5 ticks triggers cancel() which refunds items
        // into the client inventory and resets all builder arrays, causing ghost items and
        // item count flickering in the GUI.
        // On the server (disk load) or on initial client sync (buildingInfo == null), always rebuild.
        if (level == null || !level.isClientSide() || buildingInfo == null) {
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

        // Owner
        Optional<ValueInput> ownerInputOpt = input.child("owner");
        if (ownerInputOpt.isPresent()) {
            ValueInput ownerInput = ownerInputOpt.get();
            String uuidStr = ownerInput.getStringOr("uuid", "");
            String name = ownerInput.getStringOr("name", "");
            if (!uuidStr.isEmpty()) {
                try {
                    owner = new GameProfile(UUID.fromString(uuidStr), name);
                } catch (Exception e) {
                    owner = null;
                }
            }
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
        left.add("addon = " + addon);
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
        return isValid()
            ? addon != null ? addon.buildingInfo : buildingInfo
            : null;
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
        net.neoforged.neoforge.transfer.item.ItemResource resource =
                net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
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
            var handler = serverLevel.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    adj, face.getOpposite());
            if (handler == null) continue;
            int inserted = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(
                    handler, resource, remaining, null);
            remaining -= inserted;
        }
        return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
    }

    // ==================== IPlayerOwned ====================

    @Override
    public GameProfile getOwner() {
        return owner;
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
        return addon != null || box.isInitialized();
    }

    public boolean isValid() {
        if (!hasBox()) return false;
        return (addon != null ? addon.buildingInfo : buildingInfo) != null;
    }

    @Override
    public IBox getBox() {
        if (!hasBox()) {
            throw new IllegalStateException("Called getBox() when hasBox() returned false!");
        }
        return addon != null ? addon.volumeBox.box : box;
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
        this.mode = mode;
        // Push the mode change to clients. The periodic sync inside tick() is gated on
        // mode != OFF && !isFinished(), so transitions *into* OFF (or into LOOP from
        // ON-finished) would otherwise leave the client with stale state — and the
        // BER would render the wrong LED pattern until the next chunk reload.
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
