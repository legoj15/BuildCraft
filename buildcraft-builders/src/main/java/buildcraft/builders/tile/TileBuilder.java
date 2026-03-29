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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.api.core.IPathProvider;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.builders.BCBuildersBlockEntities;
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

import com.mojang.authlib.GameProfile;

public class TileBuilder extends TileBC_Neptune
    implements IDebuggable, ITileForTemplateBuilder, ITileForBlueprintBuilder, MenuProvider {

    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
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

    /** Stub fluid handler until Tank/TankManager are ported. */
    private final IFluidHandler stubFluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 0; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    public TileBuilder(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.BUILDER.get(), pos, state);
    }

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) return;
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
        if (level.isClientSide()) return;

        battery.tick(level, worldPosition);
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) {
            isDone = builder.tick();
            if (isDone) {
                if (currentBasePosIndex < basePoses.size() - 1) {
                    currentBasePosIndex++;
                    if (currentBasePosIndex >= basePoses.size()) {
                        currentBasePosIndex = basePoses.size() - 1;
                    }
                    updateSnapshot(true);
                }
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
        updateSnapshot(true);
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
        // TODO: wire up an ItemHandlerSimple when the full inventory system is integrated
        return new buildcraft.api.inventory.IItemTransactor() {
            @Override
            public ItemStack extract(buildcraft.api.core.IStackFilter filter, int min, int max, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insert(ItemStack stack, boolean allAtOnce, boolean simulate) {
                return stack;
            }
        };
    }

    @Override
    public IFluidHandler getTankManager() {
        // TODO: Replace with real TankManager once Tank/TankManager are ported
        return stubFluidHandler;
    }

    // IPlayerOwned

    @Override
    public GameProfile getOwner() {
        return null; // TODO: Store and return the owner from onPlacedBy
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
