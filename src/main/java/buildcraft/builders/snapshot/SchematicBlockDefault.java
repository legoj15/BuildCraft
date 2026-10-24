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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.misc.BlockStateNbt;
import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.block.BlockFiller;
import buildcraft.core.item.ZoneMapPortability;
import buildcraft.transport.block.BlockPipeHolder;

public class SchematicBlockDefault implements ISchematicBlock {
    private static final Logger LOGGER = LogManager.getLogger("BCBlueprint");

    /** Directions a fluid can flow from to reach this position: the four horizontals plus the
     *  block above (water at the same Y level flows horizontally, water above flows down). The
     *  block below is irrelevant — fluids don't flow upwards into a destination. Used by the
     *  fragile-block defer in {@link #build(Level, BlockPos, EnumFluidHandlingMode)}. */
    private static final Direction[] FRAGILE_FLUID_NEIGHBOUR_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    /**
     * Would {@code fluid} flowing into this state's cell wash the block away? True for the "fragile" blocks
     * ({@code canBeReplaced(fluid)}: snow layers, carpet, torches, redstone wire, …) — but never for a
     * {@link LiquidBlockContainer} (every {@code SimpleWaterloggedBlock}, e.g. pipes and markers, plus kelp and
     * seagrass). Vanilla's {@code FlowingFluid} routes those through {@code canPlaceLiquid}/{@code placeLiquid}:
     * the fluid either waterlogs the block or is held back, and the block survives either way. Without the
     * carve-out every non-solid waterloggable counted as fragile and the Builder deferred placing it beside any
     * fluid, forever.
     */
    public static boolean isWashedAwayBy(BlockState state, Fluid fluid) {
        return !(state.getBlock() instanceof LiquidBlockContainer) && state.canBeReplaced(fluid);
    }

    /** {@link #isWashedAwayBy} with water as the representative fluid. */
    public static boolean isWashedAwayByFluid(BlockState state) {
        return isWashedAwayBy(state, Fluids.WATER);
    }

    /**
     * Is there fluid beside or above {@code blockPos} that would flow in and wash {@code state} away as soon as it
     * is placed there? The fragile-block defer in {@link #build(Level, BlockPos, EnumFluidHandlingMode)}, shared
     * with the Builder's queue-time gate so a placement that build would refuse is never queued (which would
     * extract its items only to refund them).
     */
    public static boolean hasWashingFluidNeighbour(BlockGetter level, BlockPos blockPos, BlockState state) {
        return hasWashingFluidNeighbour(level, blockPos, state, neighbour -> true);
    }

    /**
     * As {@link #hasWashingFluidNeighbour(BlockGetter, BlockPos, BlockState)}, counting only the neighbours whose
     * position passes {@code which} (the Builder uses it to ask whether any washing fluid is water it will still
     * mop).
     */
    public static boolean hasWashingFluidNeighbour(BlockGetter level, BlockPos blockPos, BlockState state,
            Predicate<BlockPos> which) {
        for (Direction dir : FRAGILE_FLUID_NEIGHBOUR_DIRS) {
            BlockPos neighbourPos = blockPos.relative(dir);
            FluidState neighbour = level.getFluidState(neighbourPos);
            if (!neighbour.isEmpty() && isWashedAwayBy(state, neighbour.getType()) && which.test(neighbourPos)) {
                return true;
            }
        }
        return false;
    }

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
    /** Whether {@link #deserializeNbt} met a block, property or property value this game lacks and put a default in its place
     *  (see {@link #readFully()}). Not part of the schematic's value. */
    private boolean readWithDefaults;

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

    /**
     * The schematic a scan picks for {@code state} when the state is all that is known: no world, so no block entity
     * and no tile data. It runs every capture step of {@link #init} but the tile data one, so what the rules say of the
     * block (placement block, ignored properties, required and update offsets) is the same. For
     * {@link SchematicBlockManager#upgradeLegacyFluid}, which re-reads an entry whose tile data was never kept.
     *
     * @param readFully whether the entry this stands in for was read fully (see {@link #readFully()})
     */
    static SchematicBlockDefault ofBareState(BlockState state, boolean readFully) {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        // No level: the steps below read only the state and the block, and the one that reads the world is skipped.
        SchematicBlockContext context = new SchematicBlockContext(null, BlockPos.ZERO, BlockPos.ZERO, state,
            state.getBlock());
        schematic.captureState(context, RulesLoader.getRules(state, null));
        schematic.readWithDefaults = !readFully;
        return schematic;
    }

    /**
     * Every capture step that reads only the state and the block, in the one order they must run, shared by a scan
     * ({@link #init}) and {@link #ofBareState} so the two cannot capture different schematics for one state. The
     * place block is set before the ignored properties because the class-based ones ({@link StairBlock}'s
     * {@code shape}) are chosen from the place block: run the other way round, a capture saved a stair with
     * {@code shape} still compared, while the same schematic read back ({@link #deserializeNbt}, which has the place
     * block first) ignored it, so the snapshot key changed with one store round-trip.
     */
    private void captureState(SchematicBlockContext context, Set<JsonRule> rules) {
        setRequiredBlockOffsets(context, rules);
        setBlockState(context, rules);
        setPlaceBlock(context, rules);
        setIgnoredProperties(context, rules);
        setUpdateBlockOffsets(context, rules);
        setCanBeReplacedWithBlocks(context, rules);
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
     * schematics (via {@link #deserializeNbt} re-running the migration).
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
        // Double-plant blocks (tall grass, large fern, sunflower, lilac, rose bush, peony,
        // pitcher plant, tall seagrass) are the same two-stacked-half shape as a door: LOWER on
        // the ground, UPPER directly above, sharing one item. Vanilla updateShape turns a lone
        // half into air the moment its partner is missing, so the Builder must place both halves
        // at once. The linkage mirrors the door case exactly. (TallDryGrass is a single block and
        // is correctly skipped — it isn't a DoublePlantBlock.)
        if (block instanceof DoublePlantBlock && state != null
                && state.hasProperty(DoublePlantBlock.HALF)) {
            DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
            if (half == DoubleBlockHalf.UPPER) {
                // LOWER is directly below. Deferring UPPER on it is what stops the UPPER half
                // from ever queuing its own place task: LOWER's build() places both halves
                // atomically, so by the time LOWER is CORRECT the UPPER is already in the world
                // and the next check pass classifies it CORRECT too. Without this the UPPER
                // setBlocks alone and vanilla updateShape destroys it (no LOWER beneath), looping
                // forever — the symptom the user saw ("throws the tall grass out on repeat").
                requiredBlockOffsets.add(new BlockPos(0, -1, 0));
            } else if (half == DoubleBlockHalf.LOWER) {
                // The dirt/grass the plant grows on. Gates placement so a plant isn't queued
                // before its ground is built; harmless on existing terrain, where the offset
                // resolves to a CORRECT (or null/out-of-blueprint) neighbour and is treated as
                // satisfied. UPPER (0, +1, 0) is deliberately NOT required — LOWER's build()
                // places it, so requiring it would deadlock.
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
            // Sanitized at capture as well as at deserialization: a chest scanned in a generated structure carries
            // the loot-table keys verbatim, and blueprint safety must not depend on the store's file round-trip
            // re-running the deserialize gate (a cached capture object would bypass it).
            tileNbt = sanitizeTileNbt(tileEntity.saveWithoutMetadata(context.world.registryAccess()), context.blockState);
        }
    }

    /**
     * The capture's last step, for a schematic that is kept (the Architect's scan, a single schematic): a zone map's
     * ref names a zone in this world's store only, so each zone map in {@link #tileNbt} carries its plan for the build
     * to hand on ({@link ZoneMapPortability}). Not part of {@link #init}, which also runs for throwaway schematics (the
     * Architect's live preview) that would copy every plan for nothing.
     */
    public void carryZonePlans() {
        ZoneMapPortability.carryPlans(tileNbt);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    protected void setPlaceBlock(SchematicBlockContext context, Set<JsonRule> rules) {
        placeBlock = placeBlockFor(rules, context.block);
    }

    /**
     * The block {@code rules} say to place: the first rule that names one, in the rules' load order
     * ({@link RulesLoader#getRules(BlockState, CompoundTag)} hands them over ordered, so two rules that both name one
     * resolve the same way on every run), else {@code fallback}. Shared by the capture and the scan-safety check, so
     * the two cannot disagree.
     */
    private static Block placeBlockFor(Set<JsonRule> rules, Block fallback) {
        return rules.stream()
            .map(rule -> rule.placeBlock)
            .filter(Objects::nonNull)
            .findFirst()
            .map(Identifier::parse)
            .map(id -> buildcraft.lib.misc.RegistryUtilBC.getValue(BuiltInRegistries.BLOCK, id))
            .orElse(fallback);
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
                .map(buildcraft.lib.misc.PositionUtil::getDirectionNormal)
                .map(BlockPos.ZERO::offset)
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
            .map(id -> buildcraft.lib.misc.RegistryUtilBC.getValue(BuiltInRegistries.BLOCK, id))
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
        captureState(context, rules);
        setTileNbt(context, rules);
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
     * every container-contents contribution ({@link RequiredExtractor#isContainerContents}: chest
     * and hopper lists, a lectern's book, a jukebox's record) while keeping
     * {@link RequiredExtractorItemFromBlock} (the chest item itself) and any constant/tank extractors.
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
        // Same again for double-plant blocks: the UPPER half of a tall grass / fern / two-tall
        // flower is placed atomically by LOWER's build, so it must not list a plant item (else
        // the Builder consumes two per plant). The JSON already declares empty requiredExtractors
        // for these UPPER halves; guarding here keeps it robust and parallel to bed/door.
        if (placeBlock instanceof DoublePlantBlock && blockState != null
                && blockState.hasProperty(DoublePlantBlock.HALF)
                && blockState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
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
            .filter(extractor -> includeContainerContents || !extractor.isContainerContents())
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
    public CompoundTag getTileNbtForRender() {
        return tileNbt;
    }

    @Override
    public SchematicBlockDefault getRotated(Rotation rotation) {
        SchematicBlockDefault schematicBlock = SchematicBlockManager.createCleanCopy(this);
        requiredBlockOffsets.stream()
            .map(blockPos -> blockPos.rotate(rotation))
            .forEach(schematicBlock.requiredBlockOffsets::add);
        // A blueprint rotates in the abstract — no level, no position — so NeoForge's level-aware
        // IBlockStateExtension#rotate(LevelAccessor, BlockPos, Rotation) has nothing to be handed (its default
        // delegates straight back here anyway). The context-free form is deprecated, not for removal.
        @SuppressWarnings("deprecation")
        BlockState rotated = blockState.rotate(rotation);
        schematicBlock.blockState = rotated;
        schematicBlock.ignoredProperties.addAll(ignoredProperties);
        // A copy, like every other field: the rotated schematic is handed to builders that may adjust it.
        schematicBlock.tileNbt = tileNbt == null ? null : tileNbt.copy();
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

    /**
     * Places the block as a player would by hand (the single schematic item): no fluid handling and no fragile-block
     * defer, so a torch goes down beside water exactly as a player's would. Every Builder mode goes through
     * {@link #build(Level, BlockPos, EnumFluidHandlingMode, boolean)} instead.
     */
    @Override
    public boolean build(Level level, BlockPos blockPos) {
        return build(level, blockPos, EnumFluidHandlingMode.NO_REPLACE, true, false);
    }

    /**
     * Fluid-aware variant of {@link #build(Level, BlockPos)}. Under
     * {@link EnumFluidHandlingMode#REPLACE} or {@link EnumFluidHandlingMode#CLEAR} a
     * water source at {@code blockPos} is preserved as a waterlogged state if the
     * {@code placeBlock} supports vanilla {@code WATERLOGGED}; otherwise the fluid is
     * destroyed before placing. Lava and non-source fluids always fall through to
     * destroy-then-place. A placement that fails afterwards (a torch with no floor pops) puts
     * back what the cell held when it held a fluid source (the lava or water, or the waterlogged
     * block with its water), in every mode, so a failed build leaves the world's sources alone.
     */
    public boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode) {
        return build(level, blockPos, fluidMode, true);
    }

    /** The state {@link #build} places, before its fluid and leaf adjustments: the captured state carried over to
     *  the place block, with the ignored properties reset to the place block's defaults. */
    BlockState placedState() {
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
        return newBlockState;
    }

    /**
     * The cells {@link #build} at {@code blockPos} sets: none for a schematic that places nothing, {@code blockPos}
     * itself, then the other half's cell for a two-cell block (a bed's head, a door's or tall plant's upper half),
     * which {@code build} sets in the same call. What a machine asks protection mods about before building.
     */
    public List<BlockPos> placedCells(BlockPos blockPos) {
        if (placeBlock == Blocks.AIR) {
            return List.of();
        }
        BlockPos other = secondHalfPos(placedState(), blockPos);
        return other == null ? List.of(blockPos) : List.of(blockPos, other);
    }

    /**
     * Where the other half of the two-cell block {@code state} placed at {@code blockPos} goes, when {@code state} is
     * the half that {@link #build} places both from (a bed's FOOT, a door's or double plant's LOWER half); else null.
     * The other half's schematic places nothing itself (no required items, a linkage offset back to this one).
     */
    @Nullable
    static BlockPos secondHalfPos(BlockState state, BlockPos blockPos) {
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return blockPos.relative(state.getValue(BedBlock.FACING));
        }
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            return blockPos.above();
        }
        if (state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            return blockPos.above();
        }
        return null;
    }

    /** The other half of {@code state}, for a state {@link #secondHalfPos} names a cell for. */
    private static BlockState secondHalfState(BlockState state) {
        if (state.getBlock() instanceof BedBlock) {
            return state.setValue(BedBlock.PART, BedPart.HEAD);
        }
        if (state.getBlock() instanceof DoorBlock) {
            return state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        }
        return state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
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
    public boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode,
                         boolean includeContainerContents) {
        return build(level, blockPos, fluidMode, includeContainerContents, true);
    }

    /** {@link #build(Level, BlockPos, EnumFluidHandlingMode, boolean)}, with the fragile-block defer only when
     *  {@code deferFragile} (a Builder placement, in any mode; never a placement by hand). */
    @SuppressWarnings("Duplicates")
    private boolean build(Level level, BlockPos blockPos, EnumFluidHandlingMode fluidMode,
                          boolean includeContainerContents, boolean deferFragile) {
        if (placeBlock == Blocks.AIR) {
            return true;
        }
        BlockState newBlockState = placedState();
        // What the cell holds before this call touches it, when that carries a fluid source (a lava or water source
        // block, or a waterlogged block): the placement below overwrites it, and a placement that is undone
        // afterwards (the block popped in the updateShape sweep) puts exactly that back instead of leaving air, so a
        // failed build never costs the world its source, nor the waterlogged slab or stair that held it. The whole
        // state, not just the fluid's legacy block, because destroyBlock below turns a waterlogged block into bare
        // water. Every mode keeps it: NO_REPLACE overwrites the cell too.
        FluidState existing = level.getFluidState(blockPos);
        BlockState displacedState = existing.isSource() ? level.getBlockState(blockPos) : null;
        // Pre-compute fluid handling: under REPLACE/CLEAR, decide up-front whether the source at
        // blockPos will be waterlogged into the placed state or destroyed first. Don't actually
        // destroy yet — every "should we abort?" check (canSurvive, fragile-block defer) needs to
        // run on the unmodified world so a failing check doesn't leave the world torn down with
        // no replacement placed. The actual destroyBlock fires only after every check passes.
        boolean willDestroyFluidAtPos = false;
        if (fluidMode == EnumFluidHandlingMode.REPLACE || fluidMode == EnumFluidHandlingMode.CLEAR) {
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
        // Fragile-block defer: in every Builder mode, when the block we're about to place is one whose
        // canBeReplaced(fluid) returns true (snow_layer, carpet, button, redstone wire, torch,
        // sapling, …), any adjacent fluid will flow into the freshly placed block on the next
        // fluid tick and destroy it. canSurvive doesn't catch this because it evaluates against
        // the *current* world state, not the future state after fluid flow. Without the defer the
        // user sees a place→destroy→place loop (item consumed once per cycle until the inventory
        // drains) when REPLACE-ing snow into a leaky pool. Solid blocks aren't fragile
        // (canBeReplaced returns false) and waterloggable blocks — waterlogged or dry — coexist
        // with fluid, so isWashedAwayBy excludes both. NO_REPLACE needs it too: there the washed-away
        // cell then holds fluid, which that mode counts as built, so the block was lost for good.
        if (deferFragile) {
            boolean placedAsWaterlogged = newBlockState.hasProperty(BlockStateProperties.WATERLOGGED)
                    && newBlockState.getValue(BlockStateProperties.WATERLOGGED);
            if (!placedAsWaterlogged && hasWashingFluidNeighbour(level, blockPos, newBlockState)) {
                return false;
            }
        }
        // Two-cell pre-check (bed FOOT, door LOWER, double-plant LOWER): the other half's cell must also be clear
        // (or replaceable) for the block to be a valid two-half structure. Vanilla's item placement fails if either
        // half is blocked, leaving no block and no item consumed. The Builder's flow is similar: defer the placement
        // (refund the item via cancelPlaceTask) until the other cell is clear. Without this, the first half would
        // setBlock alone, then vanilla updateShape would destroy it on the next tick when it can't find its other
        // half, and the Builder would have already consumed the item.
        // A water source in that cell passes (it is replaceable) and is overwritten by the other half, so what the
        // cell held is remembered like the one at blockPos and restored if the placement is undone.
        BlockPos otherHalfPos = secondHalfPos(newBlockState, blockPos);
        BlockState displacedOtherState = null;
        if (otherHalfPos != null) {
            BlockState atOther = level.getBlockState(otherHalfPos);
            if (!atOther.isAir() && !atOther.canBeReplaced(Fluids.WATER)) {
                return false;
            }
            if (atOther.getFluidState().isSource()) {
                displacedOtherState = atOther;
            }
        }
        // All checks passed; commit the deferred fluid-destroy now. destroyBlock leaves the fluid's own block behind
        // (the lava or water source itself, or just the water of a waterlogged block, which it removes without
        // dropping anything: dropBlock is false, so restoring displacedState on a failure duplicates no drop); the
        // setBlock below is what finally removes the source, and the failure exit restores displacedState.
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
            // (The double plant's UPPER half likewise goes in before the sweep, so the LOWER half's UP-direction
            // updateShape sees its partner.)
            BlockPos secondHalfPos = secondHalfPos(newBlockState, blockPos);
            if (secondHalfPos != null) {
                level.setBlock(secondHalfPos, secondHalfState(newBlockState), 3);
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
                //? if >=1.21.10 {
                BlockState updated = afterShape.updateShape(
                    level, level, blockPos, dir, neighborPos, neighborState, level.getRandom()
                );
                //?} else {
                /*BlockState updated = afterShape.updateShape(
                    dir, neighborState, level, blockPos, neighborPos
                );*/
                //?}
                if (updated.isAir()) {
                    // Block self-destructed via updateShape (e.g. torch with no sturdy block
                    // below). Roll back this half AND the atomic other half (if applicable),
                    // putting back any fluid source each cell held, then return false so
                    // cancelPlaceTask refunds items.
                    rollBackCell(level, blockPos, displacedState);
                    if (secondHalfPos != null) {
                        rollBackCell(level, secondHalfPos, displacedOtherState);
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
                //? if >=1.21.10 {
                .forEach(updatePos -> level.neighborChanged(updatePos, placeBlock, null));
                //?} else {
                /*// 1.21.1 CollectingNeighborUpdater.neighborChanged calls fromPos.immutable() with no
                // null-guard (NPE), unlike modern; pass the placed block pos (the legitimate source).
                .forEach(updatePos -> level.neighborChanged(updatePos, placeBlock, blockPos));*/
                //?}
            CompoundTag newTileNbt = tileNbtToPlace(includeContainerContents);
            if (newTileNbt != null) {
                loadTileData(level, blockPos, newTileNbt);
            }
            return true;
        }
        // setBlock refused: the cell already holds the state, so there is nothing to undo. Restoring displacedState
        // here would overwrite an identical waterlogged block (a fence the schematic wants) with its own water.
        return false;
    }

    /**
     * Puts {@code pos} back after {@link #build} undoes a placement: what the cell held before the placement
     * overwrote it ({@code displacedState}, a fluid source block or a waterlogged block), or air when it held no
     * source. Flag 3 notifies the neighbours and lets a liquid block schedule its flow tick again, as it does when
     * placed in vanilla.
     */
    private static void rollBackCell(Level level, BlockPos pos, @Nullable BlockState displacedState) {
        level.setBlock(pos, displacedState != null ? displacedState : Blocks.AIR.defaultBlockState(), 3);
    }

    /**
     * The block entity data {@link #build} and {@link #buildWithoutChecks} load: a fresh copy of {@link #tileNbt}
     * (never the field, which is shared across rebuilds and rotations) with the container contents stripped when
     * {@code includeContainerContents} is false ({@link EnumContainerContentsMode#IGNORE IGNORE} mode), and every
     * matching rule's {@link JsonRule#replaceNbt} merged over it (a directive 1.12.2 applied too). That is what keeps
     * a rebuilt machine from starting with the stored power it was scanned with. The rules are matched against the
     * captured {@link #tileNbt}, so a selector on tile data sees what the Architect saw, not what the strip or an
     * earlier rule left. Null when the schematic has no tile data.
     *
     * <p>The zone plans the capture carried ({@link ZoneMapPortability}) go to this world's zone store, each map
     * pointed at its entry. Map locations are not moved with the build: a zone, point or area map still names the
     * world coordinates it named where it was captured.
     */
    @Nullable
    CompoundTag tileNbtToPlace(boolean includeContainerContents) {
        if (tileNbt == null) {
            return null;
        }
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        CompoundTag data = tileNbt.copy();
        if (!includeContainerContents) {
            stripContainerContentsFromNbt(rules, data);
        }
        CompoundTag placed = JsonRule.applyReplaceNbt(data, rules);
        ZoneMapPortability.settlePlans(placed);
        return placed;
    }

    /**
     * Whether a rule matching this schematic prices its block entity's contents: some extractor reads them out of
     * {@link #tileNbt} ({@link RequiredExtractor#pricesTileContents()}). A chest, a BuildCraft machine or tank with
     * its rule; not a campfire, nor any modded item holder no rule names.
     */
    boolean contentsArePriced() {
        return RulesLoader.getRules(blockState, tileNbt).stream()
            .map(rule -> rule.requiredExtractors)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .anyMatch(RequiredExtractor::pricesTileContents);
    }

    /**
     * Writes {@code data} ({@link #tileNbtToPlace}: a copy of {@link #tileNbt} stripped as the caller's mode requires
     * and with the rules' {@code replaceNbt} applied) into the block entity just placed at {@code blockPos}. Two
     * guards, shared by both build paths:
     * <ul>
     *   <li>Deny by default: when no rule prices the contents ({@link #contentsArePriced()}), the block entity is
     *       emptied after the load through {@link Clearable}, which the vanilla item holders and every BuildCraft
     *       tile implement over exactly their contents (a BuildCraft tile's {@code clearContent} wipes what it would
     *       spill, never its configuration; a brushed suspicious block is the vanilla holder that is not one, and is
     *       stripped at capture time by {@link #sanitizeTileNbt(CompoundTag, BlockState)}). Otherwise a legitimately
     *       scanned item holder no rule names would be rebuilt full for the price of the empty block.</li>
     *   <li>A loader that throws on the NBT (any mod's, on malformed or hand-made data) must not crash the server
     *       tick, which would recur on every restart while the Builder retries the cell. The half-loaded block entity
     *       is replaced with a fresh default one and the placement still counts, so the cell is not retried.</li>
     * </ul>
     */
    protected void loadTileData(Level level, BlockPos blockPos, CompoundTag data) {
        BlockEntity tileEntity = level.getBlockEntity(blockPos);
        if (tileEntity == null) {
            return;
        }
        data.putInt("x", blockPos.getX());
        data.putInt("y", blockPos.getY());
        data.putInt("z", blockPos.getZ());
        try {
            //? if >=1.21.10 {
            tileEntity.loadWithComponents(
                TagValueInput.create(
                    ProblemReporter.DISCARDING,
                    level.registryAccess(),
                    data
                )
            );
            //?} else {
            /*tileEntity.loadWithComponents(data, level.registryAccess());*/
            //?}
            // Inside the guard: a mod's clearContent can throw on the state its loader just built, as fatally.
            if (tileEntity instanceof Clearable clearable && !contentsArePriced()) {
                clearable.clearContent();
                tileEntity.setChanged();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Discarding the blueprint data of {} at {}: its block entity failed to load it ({})",
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(blockPos).getBlock()), blockPos,
                Snapshot.logSafe(e, 256));
            resetBlockEntity(level, blockPos);
        }
    }

    /** Replaces the block entity at {@code blockPos} with the fresh default one its block creates. */
    private static void resetBlockEntity(Level level, BlockPos blockPos) {
        BlockState state = level.getBlockState(blockPos);
        BlockEntity fresh = state.getBlock() instanceof EntityBlock entityBlock
            ? entityBlock.newBlockEntity(blockPos, state)
            : null;
        if (fresh != null) {
            level.setBlockEntity(fresh);
        } else {
            level.removeBlockEntity(blockPos);
        }
    }

    /**
     * Walk the JSON {@code rules} matching this schematic and strip every container-contents path from the
     * supplied tileNbt. Mutates {@code tileNbt} in place — callers pass a copy, never the canonical
     * {@link #tileNbt} field (which is shared across rebuilds and rotations). The rules are looked up by the caller
     * from the canonical field: {@link RulesLoader#getRules} keeps its argument as a cache key, which a tag about to
     * be mutated must not be. Package-private: {@link SchematicEntityDefault} strips an entity's NBT the same way.
     */
    static void stripContainerContentsFromNbt(Set<JsonRule> rules, CompoundTag tileNbt) {
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
            // The same pricing, replaceNbt and crash guards as build(): skipping the checks must not skip those.
            CompoundTag newTileNbt = tileNbtToPlace(true);
            if (newTileNbt != null) {
                loadTileData(level, blockPos, newTileNbt);
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
     * Mode-aware variant. The plain {@link #isBuilt(Level, BlockPos)} treats "world wet,
     * schematic dry" on a waterloggable block as built, because REPLACE-mode placement
     * opportunistically sets WATERLOGGED=true when a source sits at the placement position
     * (preserves water aesthetically) — without the leniency the schematic would mismatch and
     * the Builder would break+place forever. Under CLEAR mode the user-stated intent is the
     * opposite: clear the water, even from waterlogged blocks. So in CLEAR we want the strict
     * comparison so "world wet, schematic dry" is reported as NOT built. (The Builder's
     * {@code canPlace} refuses any fluid cell under CLEAR, so the position is then classified
     * TO_BREAK and mopped, not dried in place.) This strictness is deliberately mode-pure; the
     * one case where it must give way, a block vanilla re-floods from water the Builder cannot
     * remove, is decided by {@code BlueprintBuilder#isBlockCorrect}, which knows the build box.
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
    public CompoundTag serializeNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(
            "requiredBlockOffsets",
            NBTUtilBC.writeCompoundList(
                requiredBlockOffsets.stream()
                    .map(NBTUtilBC::writeBlockPos)
            )
        );
        nbt.put("blockState", BlockStateNbt.write(blockState));
        nbt.put(
            "ignoredProperties",
            NBTUtilBC.writeStringList(
                ignoredProperties.stream()
                    .map(Property::getName)
            )
        );
        if (tileNbt != null) {
            // A copy: a caller that edits or re-reads the returned tag must not reach this schematic's own data.
            nbt.put("tileNbt", tileNbt.copy());
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
                    // Blocks hash by identity, so set order differs between JVMs; the snapshot key hashes this NBT.
                    .sorted()
            )
        );
        return nbt;
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) throws InvalidInputDataException {
        NBTUtilBC.readCompoundList(nbt.get("requiredBlockOffsets"))
            .map(NBTUtilBC::readBlockPos)
            .forEach(requiredBlockOffsets::add);
        CompoundTag blockStateNbt = NBTUtilBC.getCompound(nbt, "blockState");
        blockState = BlockStateNbt.read(blockStateNbt);
        readWithDefaults = !BlockStateNbt.readsBack(blockStateNbt, blockState);
        // A name this block has no property for is dropped, so it too means the file holds what this game lacks.
        NBTUtilBC.readStringList(nbt.get("ignoredProperties"))
            .forEach(propertyName -> {
                Property<?> found = blockState.getProperties().stream()
                    .filter(property -> property.getName().equals(propertyName))
                    .findFirst()
                    .orElse(null);
                if (found == null) {
                    readWithDefaults = true;
                } else {
                    ignoredProperties.add(found);
                }
            });
        if (nbt.contains("tileNbt")) {
            tileNbt = sanitizeTileNbt(NBTUtilBC.getCompound(nbt, "tileNbt"), blockState);
        }
        tileRotation = NBTUtilBC.readEnum(nbt.get("tileRotation"), Rotation.class);
        if (tileRotation == null) tileRotation = Rotation.NONE;
        // The block registry answers an id it lacks with air, so each id is checked before it is looked up.
        Identifier placeBlockId = Identifier.parse(NBTUtilBC.getString(nbt, "placeBlock", ""));
        readWithDefaults |= !BuiltInRegistries.BLOCK.containsKey(placeBlockId);
        placeBlock = buildcraft.lib.misc.RegistryUtilBC.getValue(BuiltInRegistries.BLOCK, placeBlockId);
        NBTUtilBC.readCompoundList(nbt.get("updateBlockOffsets"))
            .map(NBTUtilBC::readBlockPos)
            .forEach(updateBlockOffsets::add);
        NBTUtilBC.readStringList(nbt.get("canBeReplacedWithBlocks"))
            .map(Identifier::parse)
            .forEach(id -> {
                readWithDefaults |= !BuiltInRegistries.BLOCK.containsKey(id);
                canBeReplacedWithBlocks.add(buildcraft.lib.misc.RegistryUtilBC.getValue(BuiltInRegistries.BLOCK, id));
            });
        // Migrate old schematics to current JSON rules. Schematics saved before a rule was added
        // (e.g. before walls/leaves got their connection/persistent properties listed in
        // multiple_variants.json) have empty/incomplete ignoredProperties baked in — and since
        // deserializeNbt, not init(), is what runs at load time, the old data would otherwise be
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

    /** Whether everything {@link #deserializeNbt} read resolved in this game, so writing this schematic back keeps
     *  every block it was saved with (migrations aside). False when a block, property or property value it names
     *  (an ignored property included) is missing
     *  here (its mod is not installed, or no longer has it) and a default took its place. A schematic made by a scan
     *  is always read fully. */
    boolean readFully() {
        return !readWithDefaults;
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

    /**
     * The block-entity keys a blueprint must never place: the loot-table pair hands whatever container it lands on
     * free loot when opened (vanilla's chest/barrel/dispenser/crafter loadAdditional all read it straight from the
     * tag {@code build} hands to {@code loadWithComponents}), and the {@code container_loot} component does the same
     * through the component path. A chest scanned in a generated structure carries exactly these keys, so stripping
     * them here also keeps a legitimate scan from minting loot chests. Everything else in the tag survives: machine
     * configuration (a tank's contents, an engine's gates, a chest's captured {@code Items}) is what blueprints are
     * for, and its cost is enforced by the JSON extractors.
     * <p>
     * Mutates {@code tileNbt} in place — both callers hand a freshly built tag (deserialization: freshly read;
     * the architect's capture: freshly saved) — and returns it. Package-private and static so both gates and the
     * tests share one definition.
     */
    static CompoundTag sanitizeTileNbt(CompoundTag tileNbt) {
        if (tileNbt == null) return null;
        tileNbt.remove(RandomizableContainer.LOOT_TABLE_TAG);
        tileNbt.remove(RandomizableContainer.LOOT_TABLE_SEED_TAG);
        CompoundTag components = NBTUtilBC.getCompoundOrNull(tileNbt, "components");
        if (components != null) {
            // Defensive: nothing vanilla writes the loot pair inside components, but nothing reads it there either;
            // removing it costs one call and closes a renamed-key path.
            components.remove(RandomizableContainer.LOOT_TABLE_TAG);
            components.remove(RandomizableContainer.LOOT_TABLE_SEED_TAG);
            Identifier containerLoot = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.CONTAINER_LOOT);
            if (containerLoot != null) {
                components.remove(containerLoot.toString());
            }
        }
        return tileNbt;
    }

    /**
     * {@link #sanitizeTileNbt(CompoundTag)}, plus the keys of {@code state}'s block entity that hold something no rule
     * can price and building it would therefore mint: a beehive's or bee nest's occupants (entities, under
     * {@code bees} on every node), which are rebuilt as the plain block. A decorated pot keeps both its stored item and
     * its sherds, which its rule prices ({@link RequiredExtractorPotSherds}: the sides are charged as the items the pot
     * drops when cracked).
     * <p>
     * Also the item holders that are not {@link Clearable}, which the deny-by-default clear in {@link #build} cannot
     * reach: a brushed suspicious block's {@code item} (what brushing it yields, the same key on every node). And a
     * Builder's or Filler's {@code builderState}: its queued place tasks carry the items already taken from its
     * inventory for them, which a rebuilt machine would place or hand back for nothing. The rebuilt machine starts its
     * task afresh.
     * <p>
     * A vault and a trial spawner keep nothing at all: every part of their data mints something nothing prices. A
     * vault's {@code config} names its reward loot table and its key item (one stick for any table) and its server
     * data queues rewards; a trial spawner's data registers the players owed a reward and the table it ejects, and its
     * configs (inline on any node) name the loot tables and the entities, with any NBT, it spawns. The layouts differ
     * between nodes, so the whole tag goes: each is rebuilt as the default block entity its block creates, and the
     * returned tag is a new, empty one rather than {@code tileNbt}.
     * <p>
     * A mob spawner keeps its mob types and its timings, which are what a scanned spawner is for, and loses the rest of
     * every spawn entry ({@link #reduceSpawnEntry}): the entity's own data can be anything (an item entity holding
     * anything, a mob in any gear) and an entry's {@code equipment} names a loot table. {@code SpawnData} and
     * {@code SpawnPotentials} are laid out alike on every node.
     */
    static CompoundTag sanitizeTileNbt(CompoundTag tileNbt, @Nullable BlockState state) {
        sanitizeTileNbt(tileNbt);
        if (tileNbt == null || state == null) return tileNbt;
        Block block = state.getBlock();
        CompoundTag components = NBTUtilBC.getCompoundOrNull(tileNbt, "components");
        if (block instanceof BeehiveBlock) {
            tileNbt.remove("bees");
            if (components != null) {
                removeComponent(components, DataComponents.BEES);
            }
        } else if (block instanceof DecoratedPotBlock) {
            // The sides live in "sherds" (priced by the pot's rule); a pot_decorations component is a second copy no
            // extractor reads, so it goes.
            if (components != null) {
                removeComponent(components, DataComponents.POT_DECORATIONS);
            }
        } else if (block instanceof BrushableBlock) {
            tileNbt.remove("item");
        } else if (block instanceof VaultBlock || block instanceof TrialSpawnerBlock) {
            return new CompoundTag();
        } else if (block instanceof SpawnerBlock) {
            CompoundTag next = NBTUtilBC.getCompoundOrNull(tileNbt, BaseSpawner.SPAWN_DATA_TAG);
            if (next != null) {
                tileNbt.put(BaseSpawner.SPAWN_DATA_TAG, reduceSpawnEntry(next));
            }
            ListTag potentials = NBTUtilBC.getListOrNull(tileNbt, "SpawnPotentials", Tag.TAG_COMPOUND);
            if (potentials != null) {
                ListTag kept = new ListTag();
                for (int i = 0; i < potentials.size(); i++) {
                    CompoundTag weighted = NBTUtilBC.getCompoundOrNull(potentials, i);
                    CompoundTag data = weighted == null ? null : NBTUtilBC.getCompoundOrNull(weighted, "data");
                    if (data != null) {
                        weighted.put("data", reduceSpawnEntry(data));
                        kept.add(weighted);
                    }
                }
                tileNbt.put("SpawnPotentials", kept);
            }
        } else if (block instanceof BlockBuilder || block instanceof BlockFiller) {
            tileNbt.remove("builderState");
            tileNbt.remove("builderClientData");
        }
        return tileNbt;
    }

    /** One {@code SpawnData} entry reduced to its mob type and its spawn rules (light and block-light ranges): the
     *  entity keeps only its {@code id}, and the {@code equipment} loot table goes. */
    private static CompoundTag reduceSpawnEntry(CompoundTag spawnData) {
        CompoundTag reduced = new CompoundTag();
        CompoundTag entity = NBTUtilBC.getCompoundOrNull(spawnData, "entity");
        CompoundTag keptEntity = new CompoundTag();
        String id = entity == null ? "" : NBTUtilBC.getString(entity, "id", "");
        if (!id.isEmpty()) {
            keptEntity.putString("id", id);
        }
        reduced.put("entity", keptEntity);
        CompoundTag rules = NBTUtilBC.getCompoundOrNull(spawnData, "custom_spawn_rules");
        if (rules != null) {
            reduced.put("custom_spawn_rules", rules);
        }
        return reduced;
    }

    private static void removeComponent(CompoundTag components, net.minecraft.core.component.DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id != null) {
            components.remove(id.toString());
        }
    }

    /**
     * Whether an Architect scan could have produced this schematic: {@link #predicate}'s namespace and ignore tests on
     * the captured block, minus the live-world parts, and a {@link #placeBlock} that is the one a scan writes (the
     * rule's, or the block itself). A blueprint read from an upload is reduced to air wherever this is false (see
     * {@link Blueprint#deserializeNBT}): it would otherwise place an ignored block (an ender chest), a block of a mod
     * that ships no rules, or one block priced as another. A block that read back as air because its mod is missing
     * passes: it places nothing, and the file keeps naming it.
     * <p>
     * The schematic class is part of the test: a scan types every fluid block (water, lava, oil: see
     * {@link SchematicBlockFluid#isFluidBlock}) as {@link SchematicBlockFluid} (which charges the fluid) and every
     * pipe holder as {@link SchematicBlockPipe} (which charges its pluggables), so a default entry naming lava, or a
     * default pipe carrying facades, would build them for free. A block that merely holds a fluid (a waterlogged
     * slab) is a default entry, wet or dry. Scans have typed fluid blocks that way since the port began, but the pipe
     * schematic is younger (2026-06): an older blueprint's default-typed pipe is re-read as a pipe by
     * {@link Blueprint#deserializeNBT} instead of being dropped.
     */
    boolean capturableByScan() {
        if (blockState == null || placeBlock == null) return false;
        if (SchematicBlockFluid.isFluidBlock(blockState)) return false;
        if ((blockState.getBlock() instanceof BlockPipeHolder) != (this instanceof SchematicBlockPipe)) return false;
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        if (placeBlock != placeBlockFor(rules, blockState.getBlock())) return false;
        if (blockState.isAir()) return true;
        Identifier registryName = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        if (registryName == null || !RulesLoader.READ_DOMAINS.contains(registryName.getNamespace())) return false;
        return rules.stream().noneMatch(rule -> rule.ignore);
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
