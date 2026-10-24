/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.misc.BlockStateNbt;
import buildcraft.lib.misc.NBTUtilBC;

public class SchematicBlockFluid implements ISchematicBlock {
    private BlockState blockState;
    private boolean isFlowing;
    /** Whether {@link #deserializeNbt} met a block, property or property value this game lacks (see
     *  {@link SchematicBlockDefault#readFully()}). Not part of the schematic's value. */
    private boolean readWithDefaults;

    @SuppressWarnings("unused")
    public static boolean predicate(SchematicBlockContext context) {
        return isFluidBlock(context.blockState);
    }

    /**
     * Whether {@code state} is a fluid's own block (water, lava, oil: a {@link LiquidBlock}, any block that is the
     * legacy block of the fluid it holds, or a bubble column: water with no item to charge, which
     * {@link SnapshotBuilder#isFluidOnly} counts as fluid too), as opposed to a block that merely holds a fluid: a
     * waterlogged slab, a wet pipe, kelp. Those are priced and rebuilt as themselves. Typed as fluid they would
     * charge 1000 mB of water and no item, and lose their tile data (a pipe its pipe definition and pluggables).
     */
    static boolean isFluidBlock(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return false;
        }
        return SnapshotBuilder.isFluidOnly(state) || state.getBlock() == fluid.createLegacyBlock().getBlock();
    }

    /** Whether this entry names a block that only holds a fluid (see {@link #isFluidBlock}), as a blueprint saved
     *  while every state with a fluid was typed as one does. {@link SchematicBlockManager#upgradeLegacyFluid} re-reads
     *  it as the schematic of that block. A dry block typed as fluid (a diamond block) does not count: nothing is
     *  held, and the capture gate drops it. */
    boolean namesFluidHoldingBlock() {
        return blockState != null && !blockState.getFluidState().isEmpty() && !isFluidBlock(blockState);
    }

    @Override
    public void init(SchematicBlockContext context) {
        blockState = context.blockState;
        // A source block has isSource() true; flowing blocks do not
        isFlowing = !context.blockState.getFluidState().isEmpty() &&
                    !context.blockState.getFluidState().isSource();
    }

    @Nonnull
    @Override
    public Set<BlockPos> getRequiredBlockOffsets() {
        return Stream.concat(
                Arrays.stream(Direction.values()).filter(d -> d.getAxis().isHorizontal()),
                Stream.of(Direction.DOWN)
            )
            .map(buildcraft.lib.misc.PositionUtil::getDirectionNormal)
            .map(BlockPos.ZERO::offset)
            .collect(Collectors.toSet());
    }

    @Nullable
    @Override
    public BlockState getBlockStateForRender() {
        return blockState;
    }

    @Nonnull
    @Override
    public List<FluidStack> computeRequiredFluids() {
        if (!isFlowing && !blockState.getFluidState().isEmpty()) {
            return Collections.singletonList(
                new FluidStack(blockState.getFluidState().getType(), 1000)
            );
        }
        return Collections.emptyList();
    }

    @Override
    public SchematicBlockFluid getRotated(Rotation rotation) {
        SchematicBlockFluid schematicBlock = SchematicBlockManager.createCleanCopy(this);
        schematicBlock.blockState = blockState;
        schematicBlock.isFlowing = isFlowing;
        return schematicBlock;
    }

    @Override
    public boolean canBuild(Level level, BlockPos blockPos) {
        return level.isEmptyBlock(blockPos) ||
            (!level.getFluidState(blockPos).isEmpty() &&
             !level.getFluidState(blockPos).isSource());
    }

    @Override
    public boolean build(Level level, BlockPos blockPos) {
        if (isFlowing) {
            return true;
        }
        if (level.setBlock(blockPos, blockState, 11)) {
            Stream.concat(
                Stream.of(Direction.values())
                    .map(buildcraft.lib.misc.PositionUtil::getDirectionNormal)
                    .map(BlockPos.ZERO::offset),
                Stream.of(BlockPos.ZERO)
            )
                .map(blockPos::offset)
                //? if >=1.21.10 {
                .forEach(updatePos -> level.neighborChanged(updatePos, blockState.getBlock(), null));
                //?} else {
                /*// 1.21.1 CollectingNeighborUpdater.neighborChanged NPEs on a null fromPos (no guard,
                // unlike modern); pass the placed block pos as the source.
                .forEach(updatePos -> level.neighborChanged(updatePos, blockState.getBlock(), blockPos));*/
                //?}
            return true;
        }
        return false;
    }

    @Override
    public boolean buildWithoutChecks(Level level, BlockPos blockPos) {
        return level.setBlock(blockPos, blockState, 0);
    }

    @Override
    public boolean isBuilt(Level level, BlockPos blockPos) {
        return isFlowing || blockState.equals(level.getBlockState(blockPos));
    }

    @Override
    public CompoundTag serializeNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("blockState", BlockStateNbt.write(blockState));
        nbt.putBoolean("isFlowing", isFlowing);
        return nbt;
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) throws InvalidInputDataException {
        CompoundTag blockStateNbt = NBTUtilBC.getCompound(nbt, "blockState");
        blockState = BlockStateNbt.read(blockStateNbt);
        readWithDefaults = !BlockStateNbt.readsBack(blockStateNbt, blockState);
        isFlowing = NBTUtilBC.getBoolean(nbt, "isFlowing", false);
    }

    /** Whether everything {@link #deserializeNbt} read resolved in this game (see
     *  {@link SchematicBlockDefault#readFully()}). */
    boolean readFully() {
        return !readWithDefaults;
    }

    /** Whether an Architect scan could have produced this schematic: {@link #predicate} types only a fluid's own
     *  block (see {@link #isFluidBlock}) as one. A fluid entry naming a dry block (a diamond block) charges no fluid
     *  and places that block free, and one naming a block that merely holds a fluid (a waterlogged slab) charges
     *  water and no item, so {@link Blueprint#deserializeNBT} reduces them to air (after
     *  {@link SchematicBlockManager#upgradeLegacyFluid} has re-read a holder it can). One that read back as air (its
     *  mod is gone) passes, as in {@link SchematicBlockDefault#capturableByScan()}: it places nothing. */
    boolean capturableByScan() {
        return blockState != null && (blockState.isAir() || isFluidBlock(blockState));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicBlockFluid that = (SchematicBlockFluid) o;
        return isFlowing == that.isFlowing && blockState.equals(that.blockState);
    }

    @Override
    public int hashCode() {
        int result = blockState.hashCode();
        result = 31 * result + (isFlowing ? 1 : 0);
        return result;
    }
}
