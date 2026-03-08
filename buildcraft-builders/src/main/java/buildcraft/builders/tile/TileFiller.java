/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.tile;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.IBox;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.data.Box;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.filler.FillerType;

public class TileFiller extends TileBC_Neptune
        implements IDebuggable, IFillerStatementContainer, IControllable, MenuProvider {

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
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

    public TileFiller(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.FILLER.get(), pos, state);
    }

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // TODO: Volume box and area provider lookup (requires WorldSavedDataVolumeBoxes)
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide()) {
            patternStatement.canInteract = !isLocked();
            return;
        }
        lockedTicks--;
        if (lockedTicks < 0) {
            lockedTicks = 0;
        }
        if (mode == Mode.OFF) {
            return;
        }
        // TODO: TemplateBuilder.tick() when ported
    }

    public void onStatementChange() {
        if (level != null && !level.isClientSide()) {
            // TODO: send network update for pattern when networking is ported
        }
        finished = false;
    }

    // NBT read-write — store compound sub-objects inline since ValueOutput
    // doesn't support generic put(String, Tag)

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // Battery: just store the long directly
        output.putLong("battery_mj", battery.getStored());
        output.putBoolean("canExcavate", canExcavate);
        output.putBoolean("inverted", inverted);
        output.putBoolean("finished", finished);
        output.putByte("lockedTicks", lockedTicks);
        output.putByte("mode", (byte) mode.ordinal());
        output.putBoolean("markerBox", markerBox);
        // Box: store min/max coords inline
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
        // Pattern statement: store the unique tag
        IFillerPattern pattern = patternStatement.get();
        if (pattern != null) {
            output.putString("patternTag", pattern.getUniqueTag());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Battery
        long stored = input.getLongOr("battery_mj", 0L);
        battery.addPower(stored, false);
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
        String patternTag = input.getStringOr("patternTag", "");
        if (!patternTag.isEmpty()) {
            // TODO: resolve pattern from FillerManager.registry when fully wired
        }
    }

    // IDebuggable

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
    }

    public Level getWorldBC() {
        return level;
    }

    public int getCountToPlace() {
        return 0; // TODO: builder.leftToPlace when ported
    }

    public int getCountToBreak() {
        return 0; // TODO: builder.leftToBreak when ported
    }

    public MjBattery getBattery() {
        return battery;
    }

    public BlockPos getBuilderPos() {
        return worldPosition;
    }

    public boolean canExcavate() {
        return canExcavate;
    }

    public boolean isFinished() {
        return mode != Mode.LOOP && this.finished;
    }

    public boolean isLocked() {
        return lockedTicks > 0;
    }

    // IStatementContainer

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

    // IFillerStatementContainer

    @Override
    public Level getFillerWorld() {
        return level;
    }

    @Override
    public boolean hasBox() {
        return addon != null || box.isInitialized();
    }

    public boolean isValid() {
        return hasBox();
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
        patternStatement.set(pattern, params);
        finished = false;
        lockedTicks = 3;
    }

    // IControllable

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
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.buildcraftbuilders.filler.name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerFiller(containerId, playerInv, this);
    }

    // Sync helpers for ContainerData

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
