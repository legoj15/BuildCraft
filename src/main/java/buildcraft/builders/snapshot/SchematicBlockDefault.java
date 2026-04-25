/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.misc.NBTUtilBC;

public class SchematicBlockDefault implements ISchematicBlock {
    @SuppressWarnings("WeakerAccess")
    protected final Set<BlockPos> requiredBlockOffsets = new HashSet<>();
    @SuppressWarnings("WeakerAccess")
    protected BlockState blockState;
    @SuppressWarnings("WeakerAccess")
    protected final List<Property<?>> ignoredProperties = new ArrayList<>();
    @SuppressWarnings("WeakerAccess")
    protected CompoundTag tileNbt;
    @SuppressWarnings("WeakerAccess")
    protected Rotation tileRotation = Rotation.NONE;
    @SuppressWarnings("WeakerAccess")
    protected Block placeBlock;
    @SuppressWarnings("WeakerAccess")
    protected final Set<BlockPos> updateBlockOffsets = new HashSet<>();
    @SuppressWarnings("WeakerAccess")
    protected final Set<Block> canBeReplacedWithBlocks = new HashSet<>();

    @SuppressWarnings("unused")
    public static boolean predicate(SchematicBlockContext context) {
        if (context.blockState.isAir()) {
            return false;
        }
        Identifier registryName = BuiltInRegistries.BLOCK.getKey(context.block);
        if (registryName == null) return false;
        if (!RulesLoader.READ_DOMAINS.contains(registryName.getNamespace())) return false;
        BlockEntity be = context.world.getBlockEntity(context.pos);
        CompoundTag beNbt = be != null
            ? be.saveWithoutMetadata(context.world.registryAccess())
            : null;
        return RulesLoader.getRules(context.blockState, beNbt)
            .stream()
            .noneMatch(rule -> rule.ignore);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setRequiredBlockOffsets(SchematicBlockContext context, Set<JsonRule> rules) {
        requiredBlockOffsets.clear();
        rules.stream()
            .map(rule -> rule.requiredBlockOffsets)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .forEach(requiredBlockOffsets::add);
        if (context.block instanceof FallingBlock) {
            requiredBlockOffsets.add(new BlockPos(0, -1, 0));
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setBlockState(SchematicBlockContext context, Set<JsonRule> rules) {
        blockState = context.blockState;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setIgnoredProperties(SchematicBlockContext context, Set<JsonRule> rules) {
        ignoredProperties.clear();
        rules.stream()
            .map(rule -> rule.ignoredProperties)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .flatMap(propertyName ->
                context.blockState.getProperties().stream()
                    .filter(property -> property.getName().equals(propertyName))
            )
            .forEach(ignoredProperties::add);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setTileNbt(SchematicBlockContext context, Set<JsonRule> rules) {
        tileNbt = null;
        BlockEntity tileEntity = context.world.getBlockEntity(context.pos);
        if (tileEntity != null) {
            tileNbt = tileEntity.saveWithoutMetadata(context.world.registryAccess());
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setPlaceBlock(SchematicBlockContext context, Set<JsonRule> rules) {
        placeBlock = rules.stream()
            .map(rule -> rule.placeBlock)
            .filter(Objects::nonNull)
            .findFirst()
            .map(Identifier::parse)
            .map(BuiltInRegistries.BLOCK::getValue)
            .orElse(context.block);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setUpdateBlockOffsets(SchematicBlockContext context, Set<JsonRule> rules) {
        updateBlockOffsets.clear();
        if (rules.stream().map(rule -> rule.updateBlockOffsets).anyMatch(Objects::nonNull)) {
            rules.stream()
                .map(rule -> rule.updateBlockOffsets)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(updateBlockOffsets::add);
        } else {
            Stream.of(Direction.values())
                .map(Direction::getUnitVec3i)
                .map(BlockPos::new)
                .forEach(updateBlockOffsets::add);
            updateBlockOffsets.add(BlockPos.ZERO);
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setCanBeReplacedWithBlocks(SchematicBlockContext context, Set<JsonRule> rules) {
        canBeReplacedWithBlocks.clear();
        rules.stream()
            .map(rule -> rule.canBeReplacedWithBlocks)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .map(Identifier::parse)
            .map(BuiltInRegistries.BLOCK::getValue)
            .forEach(canBeReplacedWithBlocks::add);
        canBeReplacedWithBlocks.add(context.block);
        canBeReplacedWithBlocks.add(placeBlock);
    }

    @Override
    public void init(SchematicBlockContext context) {
        BlockEntity be = context.world.getBlockEntity(context.pos);
        CompoundTag beNbt = be != null
            ? be.saveWithoutMetadata(context.world.registryAccess())
            : null;
        Set<JsonRule> rules = RulesLoader.getRules(context.blockState, beNbt);
        setRequiredBlockOffsets(context, rules);
        setBlockState(context, rules);
        setIgnoredProperties(context, rules);
        setTileNbt(context, rules);
        setPlaceBlock(context, rules);
        setUpdateBlockOffsets(context, rules);
        setCanBeReplacedWithBlocks(context, rules);
    }

    @Nonnull
    @Override
    public Set<BlockPos> getRequiredBlockOffsets() {
        return requiredBlockOffsets;
    }

    @Nonnull
    @Override
    public List<ItemStack> computeRequiredItems() {
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        List<List<RequiredExtractor>> extractorLists = rules.stream()
            .map(rule -> rule.requiredExtractors)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return (
            extractorLists.isEmpty()
                ? Stream.of(new RequiredExtractorItemFromBlock())
                : extractorLists.stream().flatMap(Collection::stream)
        )
            .flatMap(extractor -> extractor.extractItemsFromBlock(blockState, tileNbt).stream())
            .filter(stack -> !stack.isEmpty())
            .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<FluidStack> computeRequiredFluids() {
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        return rules.stream()
            .map(rule -> rule.requiredExtractors)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .flatMap(extractor -> extractor.extractFluidsFromBlock(blockState, tileNbt).stream())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public BlockState getBlockStateForRender() {
        return blockState;
    }

    @Override
    public SchematicBlockDefault getRotated(Rotation rotation) {
        SchematicBlockDefault schematicBlock = SchematicBlockManager.createCleanCopy(this);
        requiredBlockOffsets.stream()
            .map(blockPos -> blockPos.rotate(rotation))
            .forEach(schematicBlock.requiredBlockOffsets::add);
        schematicBlock.blockState = blockState.rotate(rotation);
        schematicBlock.ignoredProperties.addAll(ignoredProperties);
        schematicBlock.tileNbt = tileNbt;
        schematicBlock.tileRotation = tileRotation.getRotated(rotation);
        schematicBlock.placeBlock = placeBlock;
        updateBlockOffsets.stream()
            .map(blockPos -> blockPos.rotate(rotation))
            .forEach(schematicBlock.updateBlockOffsets::add);
        schematicBlock.canBeReplacedWithBlocks.addAll(canBeReplacedWithBlocks);
        return schematicBlock;
    }

    @Override
    public boolean canBuild(Level level, BlockPos blockPos) {
        return level.isEmptyBlock(blockPos);
    }

    @Override
    public boolean build(Level level, BlockPos blockPos) {
        return build(level, blockPos, EnumFluidHandlingMode.NO_REPLACE);
    }

    /**
     * Fluid-aware variant of {@link #build(Level, BlockPos)}. Under
     * {@link EnumFluidHandlingMode#REPLACE} or {@link EnumFluidHandlingMode#CLEAR} a
     * water source at {@code blockPos} is preserved as a waterlogged state if the
     * {@code placeBlock} supports vanilla {@code WATERLOGGED}; otherwise the fluid is
     * destroyed before placing. Lava and non-source fluids always fall through to
     * destroy-then-place.
     */
    @SuppressWarnings("Duplicates")
    public boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode) {
        if (placeBlock == Blocks.AIR) {
            return true;
        }
        BlockState newBlockState = blockState;
        if (placeBlock != blockState.getBlock()) {
            newBlockState = placeBlock.defaultBlockState();
            for (Property<?> property : blockState.getProperties()) {
                if (newBlockState.getProperties().contains(property)) {
                    newBlockState = copyProperty(property, newBlockState, blockState);
                }
            }
        }
        for (Property<?> property : ignoredProperties) {
            newBlockState = copyProperty(property, newBlockState, placeBlock.defaultBlockState());
        }
        if (fluidMode == EnumFluidHandlingMode.REPLACE || fluidMode == EnumFluidHandlingMode.CLEAR) {
            FluidState existing = level.getFluidState(blockPos);
            if (!existing.isEmpty() && existing.isSource()) {
                boolean waterloggable =
                    existing.getType() == Fluids.WATER
                        && newBlockState.hasProperty(BlockStateProperties.WATERLOGGED);
                if (waterloggable) {
                    newBlockState = newBlockState.setValue(BlockStateProperties.WATERLOGGED, true);
                } else {
                    level.destroyBlock(blockPos, false);
                }
            }
        }
        // Reject placement if the resulting block can't physically survive at this position
        // (e.g. torch with no support). Without this, vanilla setBlock with flag 11 sets the
        // state, then the immediate neighbor-update tick pops the block back off — but build()
        // already returned true, so the item is consumed without being placed. Returning false
        // here routes through SnapshotBuilder.cancelPlaceTask, which refunds the items so the
        // position can be retried once the supporting block is built.
        if (!newBlockState.canSurvive(level, blockPos)) {
            return false;
        }
        // Builder-placed leaves should behave like player-placed leaves: persistent. Schematic
        // data captured from natural foliage has persistent=false, distance=N — once placed in
        // a new spot far from a log, vanilla's tick() pushes distance to 7 and decaying() fires,
        // dropping the leaves to air. Without this override the Builder loops forever between
        // place and decay (item consumed, position re-classified as TO_PLACE next check). Mirrors
        // vanilla's LeavesBlock.getStateForPlacement, which sets PERSISTENT=true when a player
        // places a leaves item.
        if (newBlockState.getBlock() instanceof LeavesBlock
                && newBlockState.hasProperty(LeavesBlock.PERSISTENT)) {
            newBlockState = newBlockState.setValue(LeavesBlock.PERSISTENT, true);
        }
        boolean placed = level.setBlock(blockPos, newBlockState, 11);
        if (placed) {
            updateBlockOffsets.stream()
                .map(blockPos::offset)
                .forEach(updatePos -> level.neighborChanged(updatePos, placeBlock, null));
            if (tileNbt != null) {
                BlockEntity tileEntity = level.getBlockEntity(blockPos);
                if (tileEntity != null) {
                    CompoundTag newTileNbt = tileNbt.copy();
                    newTileNbt.putInt("x", blockPos.getX());
                    newTileNbt.putInt("y", blockPos.getY());
                    newTileNbt.putInt("z", blockPos.getZ());
                    tileEntity.loadWithComponents(
                        TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            level.registryAccess(),
                            newTileNbt
                        )
                    );
                }
            }
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("Duplicates")
    public boolean buildWithoutChecks(Level level, BlockPos blockPos) {
        if (level.setBlock(blockPos, blockState, 0)) {
            if (tileNbt != null) {
                BlockEntity tileEntity = level.getBlockEntity(blockPos);
                if (tileEntity != null) {
                    CompoundTag newTileNbt = tileNbt.copy();
                    newTileNbt.putInt("x", blockPos.getX());
                    newTileNbt.putInt("y", blockPos.getY());
                    newTileNbt.putInt("z", blockPos.getZ());
                    tileEntity.loadWithComponents(
                        TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            level.registryAccess(),
                            newTileNbt
                        )
                    );
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isBuilt(Level level, BlockPos blockPos) {
        if (blockState == null) return false;
        BlockState worldState = level.getBlockState(blockPos);
        if (!canBeReplacedWithBlocks.contains(worldState.getBlock())) return false;
        // The fluid-handling REPLACE/CLEAR modes can opportunistically set WATERLOGGED=true
        // on a freshly placed block when there's a water source at the position. The schematic
        // captured the block dry, so a strict equality check would re-trigger break+place every
        // tick, wasting resources. Treat "schematic dry, world wet" as built — but not the
        // reverse, so a genuinely-waterlogged schematic still demands a waterlogged world.
        if (worldState.hasProperty(BlockStateProperties.WATERLOGGED)
                && blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                && worldState.getValue(BlockStateProperties.WATERLOGGED)
                && !blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            worldState = worldState.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return blockStatesWithoutBlockEqual(blockState, worldState, ignoredProperties);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(
            "requiredBlockOffsets",
            NBTUtilBC.writeCompoundList(
                requiredBlockOffsets.stream()
                    .map(NBTUtilBC::writeBlockPos)
            )
        );
        nbt.put("blockState", NbtUtils.writeBlockState(blockState));
        nbt.put(
            "ignoredProperties",
            NBTUtilBC.writeStringList(
                ignoredProperties.stream()
                    .map(Property::getName)
            )
        );
        if (tileNbt != null) {
            nbt.put("tileNbt", tileNbt);
        }
        nbt.put("tileRotation", NBTUtilBC.writeEnum(tileRotation));
        nbt.putString("placeBlock", BuiltInRegistries.BLOCK.getKey(placeBlock).toString());
        nbt.put(
            "updateBlockOffsets",
            NBTUtilBC.writeCompoundList(
                updateBlockOffsets.stream()
                    .map(NBTUtilBC::writeBlockPos)
            )
        );
        nbt.put(
            "canBeReplacedWithBlocks",
            NBTUtilBC.writeStringList(
                canBeReplacedWithBlocks.stream()
                    .map(BuiltInRegistries.BLOCK::getKey)
                    .map(Object::toString)
            )
        );
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException {
        NBTUtilBC.readCompoundList(nbt.get("requiredBlockOffsets"))
            .map(NBTUtilBC::readBlockPos)
            .forEach(requiredBlockOffsets::add);
        blockState = NbtUtils.readBlockState(
            BuiltInRegistries.BLOCK,
            nbt.getCompoundOrEmpty("blockState")
        );
        NBTUtilBC.readStringList(nbt.get("ignoredProperties"))
            .map(propertyName ->
                blockState.getProperties().stream()
                    .filter(property -> property.getName().equals(propertyName))
                    .findFirst()
                    .orElse(null)
            )
            .filter(java.util.Objects::nonNull)
            .forEach(ignoredProperties::add);
        if (nbt.contains("tileNbt")) {
            tileNbt = nbt.getCompoundOrEmpty("tileNbt");
        }
        tileRotation = NBTUtilBC.readEnum(nbt.get("tileRotation"), Rotation.class);
        if (tileRotation == null) tileRotation = Rotation.NONE;
        placeBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(nbt.getStringOr("placeBlock", "")));
        NBTUtilBC.readCompoundList(nbt.get("updateBlockOffsets"))
            .map(NBTUtilBC::readBlockPos)
            .forEach(updateBlockOffsets::add);
        NBTUtilBC.readStringList(nbt.get("canBeReplacedWithBlocks"))
            .map(Identifier::parse)
            .map(BuiltInRegistries.BLOCK::getValue)
            .forEach(canBeReplacedWithBlocks::add);
        // Migrate old schematics to current JSON rules. Schematics saved before a rule was added
        // (e.g. before walls/leaves got their connection/persistent properties listed in
        // multiple_variants.json) have empty/incomplete ignoredProperties baked in — and since
        // deserializeNBT, not init(), is what runs at load time, the old data would otherwise be
        // authoritative forever. Re-derive ignoredProperties from the current rules so old
        // blueprints pick up new ignored-property carve-outs without requiring a re-scan.
        // Only ignoredProperties is migrated this way: the others are either rotation-baked
        // (requiredBlockOffsets / updateBlockOffsets) or potentially deliberately overridden
        // per-schematic at scan time (placeBlock / canBeReplacedWithBlocks / tileNbt).
        java.util.Set<JsonRule> currentRules = RulesLoader.getRules(blockState, tileNbt);
        java.util.Set<String> migratedIgnoredNames = new java.util.HashSet<>();
        for (Property<?> existing : ignoredProperties) {
            migratedIgnoredNames.add(existing.getName());
        }
        currentRules.stream()
            .map(rule -> rule.ignoredProperties)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(migratedIgnoredNames::add)
            .flatMap(propertyName ->
                blockState.getProperties().stream()
                    .filter(property -> property.getName().equals(propertyName))
            )
            .forEach(ignoredProperties::add);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(
        Property<T> property, BlockState dest, BlockState source
    ) {
        return dest.setValue(property, source.getValue(property));
    }

    private static boolean blockStatesWithoutBlockEqual(
        BlockState a, BlockState b, List<Property<?>> ignored
    ) {
        for (Property<?> property : a.getProperties()) {
            if (ignored.contains(property)) continue;
            if (!b.getProperties().contains(property)) return false;
            if (!propertyValuesEqual(property, a, b)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean propertyValuesEqual(
        Property<T> property, BlockState a, BlockState b
    ) {
        return a.getValue(property).equals(b.getValue(property));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicBlockDefault that = (SchematicBlockDefault) o;
        return requiredBlockOffsets.equals(that.requiredBlockOffsets) &&
            blockState.equals(that.blockState) &&
            ignoredProperties.equals(that.ignoredProperties) &&
            (tileNbt != null ? tileNbt.equals(that.tileNbt) : that.tileNbt == null) &&
            tileRotation == that.tileRotation &&
            placeBlock.equals(that.placeBlock) &&
            updateBlockOffsets.equals(that.updateBlockOffsets) &&
            canBeReplacedWithBlocks.equals(that.canBeReplacedWithBlocks);
    }

    @Override
    public int hashCode() {
        int result = requiredBlockOffsets.hashCode();
        result = 31 * result + blockState.hashCode();
        result = 31 * result + ignoredProperties.hashCode();
        result = 31 * result + (tileNbt != null ? tileNbt.hashCode() : 0);
        result = 31 * result + tileRotation.hashCode();
        result = 31 * result + placeBlock.hashCode();
        result = 31 * result + updateBlockOffsets.hashCode();
        result = 31 * result + canBeReplacedWithBlocks.hashCode();
        return result;
    }
}
