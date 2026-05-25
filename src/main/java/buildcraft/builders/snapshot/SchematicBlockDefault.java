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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    /** Directions a fluid can flow from to reach this position: the four horizontals plus the
     *  block above (water at the same Y level flows horizontally, water above flows down). The
     *  block below is irrelevant — fluids don't flow upwards into a destination. Used by the
     *  fragile-block defer in {@link #build(Level, BlockPos, EnumFluidHandlingMode)}. */
    private static final Direction[] FRAGILE_FLUID_NEIGHBOUR_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

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
        addClassBasedRequiredBlockOffsets(context.block, context.blockState);
    }

    /**
     * Class-based requiredBlockOffsets, separate from the JSON rule path so they apply both at
     * scan time (via {@link #setRequiredBlockOffsets}) and at load time for old saved
     * schematics (via {@link #deserializeNBT} re-running the migration).
     * <ul>
     *   <li>{@link FallingBlock}: requires the block below (gravel, sand, anvil, concrete
     *       powder, etc.) — preserves the existing behaviour from before this refactor.</li>
     *   <li>{@link BedBlock}: a bed is two adjacent half-blocks (FOOT + HEAD) sharing one bed
     *       item. The architect captures both halves separately, which used to mean the
     *       Builder consumed two bed items and produced a "broken" bed if the halves placed in
     *       the wrong order or if vanilla updateShape destroyed one half mid-place. We now
     *       link the halves so only the FOOT queues a place task (HEAD's required items list
     *       is also empty — see {@link #computeRequiredItems}), and the FOOT's build path
     *       atomically setBlocks both halves. The required offsets reflect the dependency:
     *       <ul>
     *         <li>HEAD requires the FOOT position (so HEAD never queues — by the time FOOT is
     *             CORRECT, HEAD is already placed and the next check pass classifies it
     *             CORRECT too).</li>
     *         <li>FOOT additionally requires the block below the HEAD position (so vanilla
     *             updateShape doesn't destroy a bed whose HEAD half lacks support — the
     *             standard block_below_required rule already covers FOOT's own
     *             block-below).</li>
     *       </ul></li>
     * </ul>
     */
    @SuppressWarnings("WeakerAccess")
    protected void addClassBasedRequiredBlockOffsets(Block block, BlockState state) {
        if (block instanceof FallingBlock) {
            requiredBlockOffsets.add(new BlockPos(0, -1, 0));
        }
        if (block instanceof BedBlock && state != null
                && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            BedPart part = state.getValue(BedBlock.PART);
            Direction facing = state.getValue(BedBlock.FACING);
            if (part == BedPart.HEAD) {
                // FOOT is at facing.opposite from HEAD (FACING points from FOOT toward HEAD).
                requiredBlockOffsets.add(BlockPos.ZERO.relative(facing.getOpposite()));
            } else if (part == BedPart.FOOT) {
                // HEAD is at facing direction; the block below HEAD is at (facing.X, -1,
                // facing.Z) relative to FOOT.
                requiredBlockOffsets.add(new BlockPos(facing.getStepX(), -1, facing.getStepZ()));
            }
        }
        if (block instanceof DoorBlock && state != null && state.hasProperty(DoorBlock.HALF)) {
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            if (half == DoubleBlockHalf.UPPER) {
                // LOWER is at (0, -1, 0) from UPPER. The block_below_required JSON rule covers
                // this for vanilla door variants, but instanceof here generalises to modded
                // doors automatically. Adding it directly to a Set is idempotent — no
                // duplicates if both paths apply.
                requiredBlockOffsets.add(new BlockPos(0, -1, 0));
            } else if (half == DoubleBlockHalf.LOWER) {
                // LOWER's block-below (the floor under the door) is already covered by the
                // standard block_below_required rule for vanilla doors; instanceof'ing it here
                // generalises to modded doors that aren't enumerated in JSON. UPPER position
                // is at (0, +1, 0) — but we don't require it as a precondition: the build()
                // path for LOWER atomically places UPPER too, so blocking on it would
                // deadlock.
                requiredBlockOffsets.add(new BlockPos(0, -1, 0));
            }
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
        addClassBasedIgnoredProperties();
    }

    /**
     * Auto-detect neighbour-aware properties by block class. The JSON rule files cover the
     * most common cases (fence/wall/pane connection bits) but vanilla has 50+ stair variants
     * across wood / stone / brick / nether / deepslate / copper / etc. — listing them all in
     * facings.json or multiple_variants.json is brittle, and it doesn't help with modded blocks.
     * Instead we instanceof-check the placeBlock against well-known shape-aware classes here.
     * <ul>
     *   <li>{@link StairBlock} — adds "shape" so isBuilt doesn't false-fail when vanilla's
     *       updateShape recomputes the corner shape against actual neighbour stairs (the
     *       schematic captured one shape based on its source-world neighbours; the placed
     *       location may have different ones, producing a mismatch even though the build is
     *       semantically complete). Without this, stairs placed near other stairs cycle in
     *       break+replace forever after a fluid-handling event nudges the corner shape.</li>
     * </ul>
     */
    @SuppressWarnings("WeakerAccess")
    protected void addClassBasedIgnoredProperties() {
        if (placeBlock instanceof StairBlock) {
            addIgnoredPropertyByName("shape");
        }
    }

    private void addIgnoredPropertyByName(String name) {
        if (blockState == null) return;
        blockState.getProperties().stream()
            .filter(p -> p.getName().equals(name))
            .filter(p -> !ignoredProperties.contains(p))
            .findFirst()
            .ifPresent(ignoredProperties::add);
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
        return computeRequiredItems(true);
    }

    /**
     * Mode-aware variant used by the Builder. When {@code includeContainerContents} is false the
     * Builder is in {@link EnumContainerContentsMode#IGNORE IGNORE} mode, so the chest will be
     * placed empty and the player shouldn't have to source its captured inventory — filter out
     * every {@link RequiredExtractorItemsList} contribution (chest contents, hopper contents,
     * etc.) while keeping {@link RequiredExtractorItemFromBlock} (the chest item itself) and any
     * constant/tank extractors.
     */
    @Nonnull
    public List<ItemStack> computeRequiredItems(boolean includeContainerContents) {
        // Bed HEAD positions don't require items: the FOOT half places both halves in one
        // shot (see build() bed special-case). Listing items here would double-count beds in
        // the resource panel and try to extract twice. The HEAD position never queues an
        // independent place task anyway (its requiredBlockOffsets defer it on FOOT), but
        // returning empty here ensures the resource display matches reality.
        if (placeBlock instanceof BedBlock && blockState != null
                && blockState.hasProperty(BedBlock.PART)
                && blockState.getValue(BedBlock.PART) == BedPart.HEAD) {
            return java.util.Collections.emptyList();
        }
        // Same as bed for doors: UPPER half of a door is placed atomically by LOWER's build,
        // so it shouldn't list a door item — that would consume two doors per door.
        if (placeBlock instanceof DoorBlock && blockState != null
                && blockState.hasProperty(DoorBlock.HALF)
                && blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return java.util.Collections.emptyList();
        }
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
            .filter(extractor -> includeContainerContents || !(extractor instanceof RequiredExtractorItemsList))
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
        return build(level, blockPos, EnumFluidHandlingMode.NO_REPLACE, true);
    }

    /**
     * Fluid-aware variant of {@link #build(Level, BlockPos)}. Under
     * {@link EnumFluidHandlingMode#REPLACE} or {@link EnumFluidHandlingMode#CLEAR} a
     * water source at {@code blockPos} is preserved as a waterlogged state if the
     * {@code placeBlock} supports vanilla {@code WATERLOGGED}; otherwise the fluid is
     * destroyed before placing. Lava and non-source fluids always fall through to
     * destroy-then-place.
     */
    public boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode) {
        return build(level, blockPos, fluidMode, true);
    }

    /**
     * Mode-aware variant of {@link #build(Level, BlockPos, EnumFluidHandlingMode)}. When
     * {@code includeContainerContents} is false (Builder in
     * {@link EnumContainerContentsMode#IGNORE IGNORE} mode), any items-list NBT paths declared
     * by the matching JSON rules (the {@code Items} tag for vanilla chests/hoppers/etc.) are
     * stripped from the tileNbt copy before {@code loadWithComponents} runs — the placed
     * container appears empty even though the architect captured its contents. Cosmetic and
     * structural tags (custom name, lock string, loot table seed, brewing fuel level, …) are
     * preserved.
     */
    @SuppressWarnings("Duplicates")
    public boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode,
                         boolean includeContainerContents) {
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
        // Pre-compute fluid handling: under REPLACE/CLEAR, decide up-front whether the source at
        // blockPos will be waterlogged into the placed state or destroyed first. Don't actually
        // destroy yet — every "should we abort?" check (canSurvive, fragile-block defer) needs to
        // run on the unmodified world so a failing check doesn't leave the world torn down with
        // no replacement placed. The actual destroyBlock fires only after every check passes.
        boolean willDestroyFluidAtPos = false;
        if (fluidMode == EnumFluidHandlingMode.REPLACE || fluidMode == EnumFluidHandlingMode.CLEAR) {
            FluidState existing = level.getFluidState(blockPos);
            if (!existing.isEmpty() && existing.isSource()) {
                boolean waterloggable =
                    existing.getType() == Fluids.WATER
                        && newBlockState.hasProperty(BlockStateProperties.WATERLOGGED);
                if (waterloggable) {
                    // REPLACE: opportunistic waterlog — preserve the water "for free" by setting
                    // WATERLOGGED=true on the placed block.
                    // CLEAR: respect the schematic's WATERLOGGED value. If the schematic
                    // captured the block dry, we keep it dry (the setBlock at the end of build()
                    // replaces the world's wet state with our dry state, clearing the water from
                    // the waterlogged block — that's the user-requested CLEAR-mode behaviour).
                    // If the schematic captured the block wet (genuinely waterlogged in the
                    // source world), preserve the wet state — even CLEAR shouldn't override
                    // explicit blueprint intent.
                    boolean schematicWantsWater = blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                        && blockState.getValue(BlockStateProperties.WATERLOGGED);
                    if (fluidMode == EnumFluidHandlingMode.REPLACE || schematicWantsWater) {
                        newBlockState = newBlockState.setValue(BlockStateProperties.WATERLOGGED, true);
                    } else {
                        // CLEAR + schematic dry: ensure the placed state is explicitly dry. The
                        // newBlockState is already from the schematic (with WATERLOGGED=false),
                        // but if a rule rebuilt it from defaultBlockState we explicitly set it.
                        newBlockState = newBlockState.setValue(BlockStateProperties.WATERLOGGED, false);
                    }
                } else {
                    willDestroyFluidAtPos = true;
                }
            }
        }
        // (Pre-setBlock canSurvive check removed — it was too pessimistic for torch-on-wall
        // setups where vanilla updateShape cascade from setBlock would set the wall's up=true
        // because of the torch above, but canSurvive runs against the *current* world state
        // where the wall's up=false. Replaced by a post-setBlock check via the updateShape
        // iteration below: if vanilla updateShape returns AIR for any direction, the block has
        // self-destructed and we undo the placement + refund items. That reflects post-cascade
        // reality, which is what actually decides whether a block survives.)
        // Fragile-block defer: in REPLACE/CLEAR, when the block we're about to place is one whose
        // canBeReplaced(fluid) returns true (snow_layer, carpet, button, redstone wire, torch,
        // sapling, …), any adjacent fluid will flow into the freshly placed block on the next
        // fluid tick and destroy it. canSurvive doesn't catch this because it evaluates against
        // the *current* world state, not the future state after fluid flow. Without the defer the
        // user sees a place→destroy→place loop (item consumed once per cycle until the inventory
        // drains) when REPLACE-ing snow into a leaky pool. Solid blocks aren't fragile
        // (canBeReplaced returns false) and waterlogged blocks coexist with their fluid, so both
        // skip the check naturally.
        if (fluidMode == EnumFluidHandlingMode.REPLACE || fluidMode == EnumFluidHandlingMode.CLEAR) {
            boolean placedAsWaterlogged = newBlockState.hasProperty(BlockStateProperties.WATERLOGGED)
                    && newBlockState.getValue(BlockStateProperties.WATERLOGGED);
            if (!placedAsWaterlogged) {
                for (Direction dir : FRAGILE_FLUID_NEIGHBOUR_DIRS) {
                    FluidState neighbour = level.getFluidState(blockPos.relative(dir));
                    if (!neighbour.isEmpty() && newBlockState.canBeReplaced(neighbour.getType())) {
                        return false;
                    }
                }
            }
        }
        // Bed FOOT pre-check: HEAD position must also be clear (or replaceable) for the bed to
        // be a valid two-half structure. Vanilla bed item placement fails if either half is
        // blocked, leaving no bed and no item consumed. The Builder's flow is similar — defer
        // FOOT placement (refund the bed item via cancelPlaceTask) until HEAD position is
        // clear. Without this, FOOT would setBlock alone, then vanilla updateShape would
        // destroy it on the next tick when it can't find its other half, and the Builder
        // would have already consumed the bed item.
        if (newBlockState.getBlock() instanceof BedBlock
                && newBlockState.hasProperty(BedBlock.PART)
                && newBlockState.getValue(BedBlock.PART) == BedPart.FOOT) {
            Direction facing = newBlockState.getValue(BedBlock.FACING);
            BlockPos headPos = blockPos.relative(facing);
            BlockState atHead = level.getBlockState(headPos);
            if (!atHead.isAir() && !atHead.canBeReplaced(Fluids.WATER)) {
                return false;
            }
        }
        // Same shape for doors: LOWER's atomic placement also sets the UPPER half. If UPPER
        // position is blocked, defer the door placement (refunds the door item).
        if (newBlockState.getBlock() instanceof DoorBlock
                && newBlockState.hasProperty(DoorBlock.HALF)
                && newBlockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = blockPos.above();
            BlockState atUpper = level.getBlockState(upperPos);
            if (!atUpper.isAir() && !atUpper.canBeReplaced(Fluids.WATER)) {
                return false;
            }
        }
        // All checks passed; commit the deferred fluid-destroy now.
        if (willDestroyFluidAtPos) {
            level.destroyBlock(blockPos, false);
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
            // Bed FOOT / Door LOWER atomic dual-half placement happens BEFORE the updateShape
            // iteration. Otherwise the iteration's "other-half direction" call to updateShape
            // would return AIR (because the other half hasn't been placed yet) and the
            // iteration's AIR-handling below would undo this half too. With both halves in
            // place first, BedBlock/DoorBlock.updateShape from the other-half direction sees
            // the matching half and returns the synced state instead of AIR. One bed/door item
            // per bed/door total — the HEAD/UPPER position's schematic returns empty required
            // items and a linkage offset, so it never queues an independent place task.
            BlockPos secondHalfPos = null;
            if (newBlockState.getBlock() instanceof BedBlock
                    && newBlockState.hasProperty(BedBlock.PART)
                    && newBlockState.getValue(BedBlock.PART) == BedPart.FOOT) {
                Direction facing = newBlockState.getValue(BedBlock.FACING);
                secondHalfPos = blockPos.relative(facing);
                BlockState headState = newBlockState.setValue(BedBlock.PART, BedPart.HEAD);
                level.setBlock(secondHalfPos, headState, 3);
            } else if (newBlockState.getBlock() instanceof DoorBlock
                    && newBlockState.hasProperty(DoorBlock.HALF)
                    && newBlockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                secondHalfPos = blockPos.above();
                BlockState upperState = newBlockState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                level.setBlock(secondHalfPos, upperState, 3);
            }
            // Force the placed block to recompute neighbour-aware properties (glass-pane /
            // fence / wall connection bits, stair shape, redstone wire connections). Vanilla
            // setBlock with flag 11 notifies neighbours of the change so THEY can update their
            // connections to the placed block, but doesn't call updateShape on the placed
            // block itself — meaning a glass pane placed by the Builder stays as a single
            // column even when surrounded by valid connectible blocks. Iterating each
            // direction and applying updateShape mirrors vanilla's getStateForPlacement chain.
            // Doubles as the post-setBlock canSurvive check: if updateShape returns AIR the
            // block decided it can't survive at this position (e.g. torch with no support
            // below), so undo the placement and refund the items via cancelPlaceTask. This
            // replaces the prior pre-setBlock canSurvive check, which was too pessimistic for
            // torch-on-wall setups where vanilla's cascade from setBlock would set the wall's
            // up=true and thus support the torch.
            BlockState afterShape = newBlockState;
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = blockPos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                BlockState updated = afterShape.updateShape(
                    level, level, blockPos, dir, neighborPos, neighborState, level.getRandom()
                );
                if (updated.isAir()) {
                    // Block self-destructed via updateShape (e.g. torch with no sturdy block
                    // below). Roll back this half AND the atomic other half (if applicable),
                    // then return false so cancelPlaceTask refunds items.
                    level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                    if (secondHalfPos != null) {
                        level.setBlock(secondHalfPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    return false;
                }
                if (!updated.equals(afterShape)) {
                    level.setBlock(blockPos, updated, 3);
                    afterShape = updated;
                }
            }
            updateBlockOffsets.stream()
                .map(blockPos::offset)
                .forEach(updatePos -> level.neighborChanged(updatePos, placeBlock, null));
            if (tileNbt != null) {
                BlockEntity tileEntity = level.getBlockEntity(blockPos);
                if (tileEntity != null) {
                    CompoundTag newTileNbt = tileNbt.copy();
                    if (!includeContainerContents) {
                        stripContainerContentsFromNbt(newTileNbt);
                    }
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

    /**
     * Walk the JSON rules for this schematic and strip every items_list path from the supplied
     * tileNbt. Mutates {@code tileNbt} in place — callers pass a copy, never the canonical
     * {@link #tileNbt} field (which is shared across rebuilds and rotations).
     */
    private void stripContainerContentsFromNbt(CompoundTag tileNbt) {
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        for (JsonRule rule : rules) {
            if (rule.requiredExtractors == null) continue;
            for (RequiredExtractor extractor : rule.requiredExtractors) {
                extractor.clearItemsFromBlock(tileNbt);
            }
        }
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
        return isBuilt(level, blockPos, EnumFluidHandlingMode.NO_REPLACE);
    }

    /**
     * Returns true if this schematic, under {@code fluidMode}, would treat a placement at
     * {@code blockPos} as a "dry the existing block" operation — i.e. the world already holds
     * the same block as the schematic, but with WATERLOGGED=true while the schematic captured
     * WATERLOGGED=false. Only meaningful in CLEAR mode (REPLACE/NO_REPLACE leave waterlogged
     * blocks alone). The Builder uses this to skip item extraction for the place task: there's
     * no block to be physically built, just a waterlog property to toggle, so consuming a fresh
     * item from inventory would be wasteful (the user already has the block they want, just
     * with water in it).
     */
    public boolean isWaterlogClearOnly(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode) {
        if (fluidMode != EnumFluidHandlingMode.CLEAR) return false;
        if (blockState == null) return false;
        BlockState worldState = level.getBlockState(blockPos);
        if (worldState.getBlock() != blockState.getBlock()) return false;
        if (!worldState.hasProperty(BlockStateProperties.WATERLOGGED)
                || !blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        return worldState.getValue(BlockStateProperties.WATERLOGGED)
                && !blockState.getValue(BlockStateProperties.WATERLOGGED);
    }

    /**
     * Mode-aware variant. The plain {@link #isBuilt(Level, BlockPos)} treats "world wet,
     * schematic dry" on a waterloggable block as built, because REPLACE-mode placement
     * opportunistically sets WATERLOGGED=true when a source sits at the placement position
     * (preserves water aesthetically) — without the leniency the schematic would mismatch and
     * the Builder would break+place forever. Under CLEAR mode the user-stated intent is the
     * opposite: clear the water, even from waterlogged blocks. So in CLEAR we want the strict
     * comparison so "world wet, schematic dry" is reported as NOT built and the position gets
     * re-classified as TO_PLACE; the build path then sets the block dry, which (since waterlog
     * is the entire fluid state for a non-LiquidBlock waterloggable block) clears the water.
     */
    public boolean isBuilt(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode) {
        if (blockState == null) return false;
        BlockState worldState = level.getBlockState(blockPos);
        if (!canBeReplacedWithBlocks.contains(worldState.getBlock())) return false;
        if (fluidMode != EnumFluidHandlingMode.CLEAR
                && worldState.hasProperty(BlockStateProperties.WATERLOGGED)
                && blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                && worldState.getValue(BlockStateProperties.WATERLOGGED)
                && !blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            // NO_REPLACE / REPLACE: lenient — accept world-wet against schematic-dry.
            // (CLEAR falls through to the strict comparison below.)
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
        // Same class-based migration for old saves: fresh scans go through setIgnoredProperties
        // → addClassBasedIgnoredProperties; saved-then-loaded schematics need the same auto-
        // detection applied here so old blueprints pick up StairBlock-shape ignoring etc.
        addClassBasedIgnoredProperties();
        // Same migration for class-based requiredBlockOffsets — old saved bed schematics had
        // no HEAD-FOOT linkage and would consume two bed items per bed. Re-running the helper
        // here adds the right offsets so the builder behaviour matches a fresh scan.
        if (placeBlock != null) {
            addClassBasedRequiredBlockOffsets(placeBlock, blockState);
        }
        // Migrate requiredBlockOffsets from current JSON rules when the saved set is empty.
        // Schematics scanned before a JSON-rule modernisation can have empty offsets baked in
        // (e.g. blueprints scanned when facings.json still used legacy `minecraft:torch[facing=up]`
        // selectors that didn't match the modern `minecraft:torch` block) — without offsets, a
        // torch's isReadyToPlace returns true vacuously and queues regardless of whether its
        // support is built, producing the throw→undo→refund→re-queue loop the user sees as
        // "torches floating in the sky on repeat." Only populate from rules if saved is empty:
        // a non-empty saved set was written deliberately (potentially with rotation applied) and
        // adding un-rotated rule offsets on top would over-defer rotated blueprints. Old broken
        // saves are exactly the ones with empty sets, which is the case we want to migrate.
        if (requiredBlockOffsets.isEmpty()) {
            currentRules.stream()
                .map(rule -> rule.requiredBlockOffsets)
                .filter(Objects::nonNull)
                .flatMap(java.util.Collection::stream)
                .forEach(requiredBlockOffsets::add);
        }
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
