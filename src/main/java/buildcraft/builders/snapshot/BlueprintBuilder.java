/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.net.PacketBufferBC;

@SuppressWarnings("unchecked")
public class BlueprintBuilder extends SnapshotBuilder<ITileForBlueprintBuilder> {
    private static final double MAX_ENTITY_DISTANCE = 0.1D;
    private static final String FLUID_STACK_KEY = "BuilderFluidStack";

    private List<ItemStack>[] remainingDisplayRequiredBlocks;
    private List<ItemStack> remainingDisplayRequiredBlocksConcat = Collections.emptyList();
    public List<ItemStack> remainingDisplayRequired = new ArrayList<>();
    /** The merged, still-missing ITEM requirements (no fluid buckets), block placements plus the
     *  to-spawn entities' items — the same content as {@link #remainingDisplayRequired} minus the
     *  fluid-as-filled-bucket display artefacts. Server-only in practice (the client never has a
     *  building info); read by the robot builder board to decide what to fetch next. */
    public final List<ItemStack> remainingRequiredItems = new ArrayList<>();
    private List<ItemStack>[] remainingRequiredItemBlocks;
    private List<ItemStack> remainingRequiredItemBlocksConcat = Collections.emptyList();
    private final Map<Pair<List<ItemStack>, List<FluidStack>>, Optional<List<ItemStack>>> extractRequiredCache =
        new HashMap<>();

    public BlueprintBuilder(ITileForBlueprintBuilder tile) {
        super(tile);
    }

    private ISchematicBlock getSchematicBlock(BlockPos blockPos) {
        return getBuildingInfo().box.contains(blockPos)
            ?
            getBuildingInfo().rotatedPalette.get(
                getBuildingInfo().getSnapshot().data[getBuildingInfo().getSnapshot().posToIndex(
                    getBuildingInfo().fromWorld(blockPos)
                )]
            )
            : null;
    }

    @Override
    protected boolean isAir(BlockPos blockPos) {
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (schematic == null) return true;
        if (schematic.isAir()) return true;
        // CLEAR-mode override: positions where the blueprint itself specifies a fluid (the
        // architect captured a water source / lava / oil cell) are treated as air for
        // classification purposes, so {@link SnapshotBuilder#check} routes them into the air
        // branch and a world-fluid there gets marked TO_BREAK and swept up by the mop. The
        // earlier CLEAR spec preserved blueprint-fluid ("don't oscillate on water-feature
        // blueprints"), but the user-stated intent of CLEAR is "clear all fluids in the build
        // area" — water-feature blueprints can use REPLACE instead. Without this carve-out a
        // schematic that captured water around solid features (snow trails through a stream,
        // a flooded ruin reproduction) would leave every captured-fluid position untouched in
        // CLEAR, defeating the mode entirely.
        if (schematic instanceof SchematicBlockFluid && tile.getFluidMode() == EnumFluidHandlingMode.CLEAR) {
            return true;
        }
        return false;
    }

    @Override
    protected Blueprint.BuildingInfo getBuildingInfo() {
        return tile.getBlueprintBuildingInfo();
    }

    @Override
    public void updateSnapshot() {
        super.updateSnapshot();
        // noinspection unchecked
        remainingDisplayRequiredBlocks = (List<ItemStack>[]) new List<?>[getBuildingInfo().getSnapshot().getDataSize()];
        Arrays.fill(remainingDisplayRequiredBlocks, Collections.emptyList());
        // noinspection unchecked
        remainingRequiredItemBlocks = (List<ItemStack>[]) new List<?>[getBuildingInfo().getSnapshot().getDataSize()];
        Arrays.fill(remainingRequiredItemBlocks, Collections.emptyList());
    }

    @Override
    public void resourcesChanged() {
        super.resourcesChanged();
        extractRequiredCache.clear();
    }

    /**
     * Re-bake {@link #remainingDisplayRequiredBlocks} for every TO_PLACE position from the now-
     * current {@code toPlaceRequiredItems}/{@code toPlaceRequiredFluids} arrays. Called from
     * {@link buildcraft.builders.tile.TileBuilder#cycleContainerContentsMode} after the
     * BuildingInfo's required-items arrays have been swapped. Without this, the resource panel
     * keeps showing the pre-toggle cost until the SnapshotBuilder's lazy {@code check()} pass
     * eventually wraps around to each position — many ticks for a large blueprint.
     * <p>
     * Doesn't touch positions classified CORRECT (display empty) or TO_BREAK (display empty); the
     * mode toggle doesn't change their classification or their displayed cost.
     */
    public void refreshDisplayForContentsMode() {
        if (remainingDisplayRequiredBlocks == null) return;
        // checkResults is allocated by super.updateSnapshot — same lifecycle.
        if (checkResults == null) return;
        if (getBuildingInfo() == null) return;
        for (int i = 0; i < remainingDisplayRequiredBlocks.length; i++) {
            if (checkResults[i] == CHECK_RESULT_TO_PLACE) {
                remainingDisplayRequiredBlocks[i] = getDisplayRequired(
                        getBuildingInfo().toPlaceRequiredItems[i],
                        getBuildingInfo().toPlaceRequiredFluids[i]
                ).collect(Collectors.toList());
                remainingRequiredItemBlocks[i] = getRequiredItems(i);
            } else {
                remainingDisplayRequiredBlocks[i] = Collections.emptyList();
                remainingRequiredItemBlocks[i] = Collections.emptyList();
            }
        }
        afterChecks();
    }

    @Override
    public void cancel() {
        super.cancel();
        remainingDisplayRequiredBlocks = null;
        remainingRequiredItemBlocks = null;
        remainingRequiredItems.clear();
    }

    private Stream<ItemStack> getDisplayRequired(List<ItemStack> requiredItems, List<FluidStack> requiredFluids) {
        return Stream.concat(
            requiredItems == null ? Stream.empty() : requiredItems.stream(),
            requiredFluids == null ? Stream.empty() : requiredFluids.stream()
                .map(fluidStack -> {
                    // Create a bucket representation for display purposes
                    ItemStack bucket = buildcraft.lib.misc.FluidUtilBC.getFilledBucket(fluidStack);
                    return bucket;
                })
        );
    }

    private Optional<List<ItemStack>> tryExtractRequired(List<ItemStack> requiredItems,
                                                         List<FluidStack> requiredFluids,
                                                         boolean simulate) {
        Supplier<Optional<List<ItemStack>>> function = () ->
            (
                StackUtil.mergeSameItems(requiredItems).stream()
                    .noneMatch(stack ->
                        tile.getInvResources().extract(
                            extracted -> StackUtil.canMerge(stack, extracted),
                            stack.getCount(),
                            stack.getCount(),
                            true
                        ).isEmpty()
                    ) &&
                    FluidUtilBC.mergeSameFluids(requiredFluids).stream()
                        .allMatch(stack -> {
                            //? if >=1.21.10 {
                            try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                                int extracted = tile.getTankManager().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(stack), stack.getAmount(), tx);
                                return extracted == stack.getAmount();
                            }
                            //?} else {
                            /*int extracted = tile.getTankManager().drain(stack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).getAmount();
                            return extracted == stack.getAmount();*/
                            //?}
                        })
            )
                ?
                Optional.of(
                    StackUtil.mergeSameItems(
                        Stream.concat(
                            requiredItems.stream()
                                .map(stack ->
                                    tile.getInvResources().extract(
                                        extracted -> StackUtil.canMerge(stack, extracted),
                                        stack.getCount(),
                                        stack.getCount(),
                                        simulate
                                    )
                                ),
                            FluidUtilBC.mergeSameFluids(requiredFluids).stream()
                                .map(fluidStack -> {
                                    //? if >=1.21.10 {
                                    try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                                        int extracted = tile.getTankManager().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluidStack), fluidStack.getAmount(), tx);
                                        if (!simulate) tx.commit();
                                        return new FluidStack(fluidStack.getFluid(), extracted);
                                    }
                                    //?} else {
                                    /*int extracted = tile.getTankManager().drain(fluidStack, simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
                                    return new FluidStack(fluidStack.getFluid(), extracted);*/
                                    //?}
                                })
                                .map(fluidStack -> {
                                    ItemStack stack = buildcraft.lib.misc.FluidUtilBC.getFilledBucket(fluidStack);
                                    // Store fluid id + amount directly in the BuilderFluidStack
                                    // tag so cancelPlaceTask can reconstitute the FluidStack for
                                    // the tank refund. Earlier this used a TODO-stubbed empty
                                    // CompoundTag, which made cancel crash the server with
                                    // "Expected resource to be non-empty" as soon as a fluid
                                    // task got cancelled.
                                    CompoundTag fluidTag = new CompoundTag();
                                    net.minecraft.resources.Identifier fluidId =
                                        net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
                                    if (fluidId != null) {
                                        fluidTag.putString("fluid", fluidId.toString());
                                        fluidTag.putInt("amount", fluidStack.getAmount());
                                    }
                                    CompoundTag wrapper = new CompoundTag();
                                    wrapper.put(FLUID_STACK_KEY, fluidTag);
                                    if (!stack.isEmpty()) {
                                        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                            net.minecraft.world.item.component.CustomData.of(wrapper));
                                    }
                                    return stack;
                                })
                        ).collect(Collectors.toList())
                    )
                )
                : Optional.empty();
        if (!simulate) {
            return function.get();
        }
        return extractRequiredCache.computeIfAbsent(
            Pair.of(requiredItems, requiredFluids),
            pair -> function.get()
        );
    }

    @Override
    protected boolean canPlace(BlockPos blockPos) {
        // noinspection ConstantConditions
        if (isAir(blockPos)) return false;
        EnumFluidHandlingMode mode = tile.getFluidMode();
        boolean hasFluid = !tile.getWorldBC().getFluidState(blockPos).isEmpty();
        if (hasFluid) {
            // CLEAR: force the position to TO_BREAK (canPlace returns false → check() routes
            // into the TO_BREAK branch). Without this, schematic-non-air-non-fluid + world-fluid
            // positions (e.g. schematic-snow + world-water-source where the user placed the
            // blueprint in a watery area) sit in TO_PLACE forever — the mop gate defers them
            // because the area still has fluid, and the area still has fluid because the fluid
            // AT THIS POSITION is what's keeping the gate closed. Routing them through
            // TO_BREAK first lets the mop sweep the fluid; the position re-classifies as
            // TO_PLACE on the next check pass once the world position is empty, and the place
            // runs after the rest of the area is also dry.
            if (mode == EnumFluidHandlingMode.CLEAR) return false;
            // REPLACE: the place path (SchematicBlockDefault#build with fluidMode) waterlogs
            // or destroys the fluid in the same pass. No need to break separately first.
            // Without this short-circuit the stock schematic.canBuild check would see
            // level.isEmptyBlock == false and refuse, leaving the fluid position untouched.
            if (mode == EnumFluidHandlingMode.REPLACE) return true;
        }
        return getSchematicBlock(blockPos).canBuild(tile.getWorldBC(), blockPos);
    }

    @Override
    protected boolean isReadyToPlace(BlockPos blockPos) {
        // noinspection ConstantConditions
        ISchematicBlock self = getSchematicBlock(blockPos);
        boolean selfIsFluid = self instanceof SchematicBlockFluid;
        boolean dependenciesMet = self.getRequiredBlockOffsets().stream()
            .map(blockPos::offset)
            .allMatch(pos -> {
                ISchematicBlock neighbour = getSchematicBlock(pos);
                if (neighbour == null) return true;
                if (checkResults[posToIndex(pos)] == CHECK_RESULT_CORRECT) return true;
                // Fluid-to-fluid dependency deadlock: two adjacent source blocks each list the
                // other in their required offsets, so under the strict rule each waits for the
                // other to be placed first and neither ever gets placed. Source fluids don't
                // actually need each other built first — once both are placed the flowing
                // cells they temporarily create in each other's positions get overwritten by
                // the source placements. Skip this single inter-fluid constraint; the
                // fluid-vs-wall constraints are preserved by the other neighbours.
                if (selfIsFluid && neighbour instanceof SchematicBlockFluid) return true;
                return false;
            }) && self.isReadyToBuild(tile.getWorldBC(), blockPos);
        if (!dependenciesMet) return false;
        // Pre-check canSurvive against the current world. Without this, blocks like snow-on-air
        // (where the schematic itself has air at the support position, so requiredBlockOffsets
        // is vacuously CORRECT but no real support exists) or snow-on-partial-snow-below (where
        // the support block exists but its top face isn't full) get queued, throw via the place
        // task animation, the post-setBlock updateShape iteration sees no support and undoes
        // the placement + refunds items, then re-classifies the position as TO_PLACE for the
        // next cycle — wasted animation cycles and battery per loop. Defer impossible-now
        // placements at queue time so they sit in TO_PLACE without ever attempting a throw.
        // Trade-off: a torch on top of a wall whose `up=false` (rare, when the wall's
        // connections add up to a "no post" pattern in vanilla's shouldRaisePost) will defer
        // permanently — vanilla's normal player-placement-cascade would set up=true upon torch
        // placement, but we never get there. If you hit it, manually placing the torch works.
        if (self instanceof SchematicBlockDefault def && def.blockState != null
                && !def.blockState.canSurvive(tile.getWorldBC(), blockPos)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean isFragileSchematicAt(BlockPos blockPos) {
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (!(schematic instanceof SchematicBlockDefault def)) return false;
        if (def.blockState == null) return false;
        // Water stands in for every fluid: the blocks it washes away (snow_layer, carpet, button,
        // redstone wire, sapling, torch, …) are the ones any fluid flow displaces. Waterloggables
        // (pipes, markers, …) are excluded — fluid waterlogs them or is held back.
        return SchematicBlockDefault.isWashedAwayByFluid(def.blockState);
    }

    @Override
    protected boolean isFragileWashedAwayAt(BlockPos blockPos) {
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (!(schematic instanceof SchematicBlockDefault def) || def.blockState == null) return false;
        return SchematicBlockDefault.hasWashingFluidNeighbour(tile.getWorldBC(), blockPos, def.blockState);
    }

    @Override
    protected boolean hasEnoughToPlaceItems(BlockPos blockPos) {
        return tryExtractRequired(
            getBuildingInfo().toPlaceRequiredItems[posToIndex(blockPos)],
            getBuildingInfo().toPlaceRequiredFluids[posToIndex(blockPos)],
            true
        ).isPresent();
    }

    @Override
    protected List<ItemStack> getToPlaceItems(BlockPos blockPos) {
        return tryExtractRequired(
            getBuildingInfo().toPlaceRequiredItems[posToIndex(blockPos)],
            getBuildingInfo().toPlaceRequiredFluids[posToIndex(blockPos)],
            false
        ).orElse(null);
    }

    @Override
    protected void cancelPlaceTask(PlaceTask placeTask) {
        super.cancelPlaceTask(placeTask);
        // Return item stacks to inventory (non-fluid items)
        placeTask.items.stream()
            .filter(stack -> {
                net.minecraft.world.item.component.CustomData customData =
                    stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                return customData == null || !customData.copyTag().contains(FLUID_STACK_KEY);
            })
            .forEach(stack -> tile.getInvResources().insert(stack, false, false));
        // Return fluids to tanks
        placeTask.items.stream()
            .filter(stack -> {
                net.minecraft.world.item.component.CustomData customData =
                    stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                return customData != null && customData.copyTag().contains(FLUID_STACK_KEY);
            })
            .map(stack -> {
                net.minecraft.world.item.component.CustomData customData =
                    stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                CompoundTag fluidTag = NBTUtilBC.getCompound(customData.copyTag(), FLUID_STACK_KEY);
                if (fluidTag.isEmpty()) return FluidStack.EMPTY;
                String fluidIdStr = NBTUtilBC.getString(fluidTag, "fluid", "");
                int amount = NBTUtilBC.getInt(fluidTag, "amount", 0);
                if (fluidIdStr.isEmpty() || amount <= 0) return FluidStack.EMPTY;
                net.minecraft.resources.Identifier id =
                    net.minecraft.resources.Identifier.tryParse(fluidIdStr);
                if (id == null) return FluidStack.EMPTY;
                net.minecraft.world.level.material.Fluid fluid =
                    buildcraft.lib.misc.RegistryUtilBC.getValue(net.minecraft.core.registries.BuiltInRegistries.FLUID, id);
                if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return FluidStack.EMPTY;
                return new FluidStack(fluid, amount);
            })
            // Defensive filter — if for any reason the round-trip above came back empty (old
            // save data from before this serialization existed, a fluid whose id has since
            // been unregistered, …) skip it rather than crashing tankManager.insert on
            // FluidResource.EMPTY.
            .filter(fluidStack -> !fluidStack.isEmpty() && fluidStack.getAmount() > 0)
            .forEach(fluidStack -> {
                //? if >=1.21.10 {
                try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    tile.getTankManager().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluidStack), fluidStack.getAmount(), tx);
                    tx.commit();
                }
                //?} else {
                /*tile.getTankManager().fill(fluidStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);*/
                //?}
            });
    }

    @Override
    protected boolean isBlockCorrect(BlockPos blockPos) {
        // noinspection ConstantConditions
        if (getBuildingInfo() == null) return false;
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (schematic == null) return false;
        // NO_REPLACE never replaces fluid: a cell holding nothing but fluid cannot be built in that mode (canPlace
        // refuses it, and the break queue refuses fluid outside CLEAR), so it is as built as this mode makes it.
        // Otherwise it stayed "to break" for ever and the build never finished. The next check sweep after the fluid
        // goes sends it to TO_PLACE and the block is built. A schematic that wants fluid itself still compares.
        if (tile.getFluidMode() == EnumFluidHandlingMode.NO_REPLACE && !(schematic instanceof SchematicBlockFluid)
                && isFluidOnly(tile.getWorldBC().getBlockState(blockPos))) {
            return true;
        }
        // Pass fluidMode through to the SchematicBlockDefault overload so CLEAR mode reports
        // "world wet, schematic dry" as NOT built (the cell is then routed to TO_BREAK, since canPlace
        // refuses any fluid cell under CLEAR, and mopped). REPLACE/NO_REPLACE keep the lenient
        // comparison via the no-mode default. Other ISchematicBlock impls (SchematicBlockFluid,
        // SchematicBlockAir) don't care about waterlogging mode.
        if (schematic instanceof SchematicBlockDefault def) {
            EnumFluidHandlingMode mode = tile.getFluidMode();
            if (def.isBuilt(tile.getWorldBC(), blockPos, mode)) return true;
            // Strict CLEAR rejected it. If the ONLY difference is that the world block is waterlogged
            // where the schematic is dry, and vanilla will flood the cell again no matter how often the
            // Builder dries it, tolerate the water: the lenient (NO_REPLACE) comparison isolates exactly
            // that waterlogging difference.
            if (mode == EnumFluidHandlingMode.REPLACE) return isFragileUnbuildableAt(blockPos, def);
            if (mode != EnumFluidHandlingMode.CLEAR) return false;
            if (def.isBuilt(tile.getWorldBC(), blockPos, EnumFluidHandlingMode.NO_REPLACE)
                && isResourcedByUnmoppableFluid(blockPos, tile.getWorldBC().getFluidState(blockPos).getType())) {
                return true;
            }
            return isFragileUnbuildableAt(blockPos, def);
        }
        return schematic.isBuilt(tile.getWorldBC(), blockPos);
    }

    /**
     * CLEAR and REPLACE: can the fragile block the schematic wants here (snow layer, torch, flower, …) never stand,
     * because fluid the Builder will never remove is beside or above the cell and washes it away the moment it is
     * placed? Such a cell is accepted as done, left empty (or holding the fluid that flows in), so the build can
     * finish: the place gate (and {@code SchematicBlockDefault#build}'s fragile defer) would otherwise defer it for
     * ever while the cell still counts as unbuilt, and the Builder would report busy without end.
     *
     * <p>REPLACE never removes fluid, so any fluid in the cell may stay, and a washing neighbour is permanent unless
     * the Builder is still to build a block into it that displaces its fluid ({@link #isStayingUnderReplace}). The
     * rest of this comment is about CLEAR, which mops.
     *
     * <p>Any fluid counts (water, lava, oil, a modded fluid): every washing neighbour must be
     * {@link #isUnmoppable permanent fluid}, and the cell itself must hold nothing to break, which is air or
     * fluid vanilla gives it from that permanent fluid: flowing fluid, or a SOURCE that fluid's own infinite-source
     * rule re-makes ({@link #isResourcedByUnmoppableFluid}; mopping it is futile, the same two sources re-make it
     * within a fluid tick). Any other source in the cell is still mopped, after which the cell refills with flowing
     * fluid and is accepted. A lava source in the cell is therefore mopped unless the {@code lava_source_conversion}
     * gamerule is on, as a modded fluid's is unless its fluid type converts to sources.
     *
     * <p>Accepted cells cannot keep each other accepted once the real fluid is gone. Flowing fluid drains as soon as
     * its permanent feeder goes; a re-made source counts only while two unmoppable horizontal sources over a solid or
     * unmoppable-source floor flank it, exactly the configuration in which vanilla really does keep it a source. Either
     * way, once that stops holding the next check sweep sends the cell back to {@code TO_PLACE} and the block is built.
     */
    private boolean isFragileUnbuildableAt(BlockPos blockPos, SchematicBlockDefault def) {
        if (def.blockState == null || !SchematicBlockDefault.isWashedAwayByFluid(def.blockState)) return false;
        boolean replace = tile.getFluidMode() == EnumFluidHandlingMode.REPLACE;
        BlockState world = tile.getWorldBC().getBlockState(blockPos);
        if (!world.isAir()) {
            if (!isFluidOnly(world)) return false;
            FluidState fluid = world.getFluidState();
            if (fluid.isEmpty()) return false;
            if (!replace && fluid.isSource() && !isResourcedByUnmoppableFluid(blockPos, fluid.getType())) return false;
        }
        Predicate<BlockPos> permanent = replace ? this::isStayingUnderReplace : this::isUnmoppable;
        Level level = tile.getWorldBC();
        return SchematicBlockDefault.hasWashingFluidNeighbour(level, blockPos, def.blockState)
            && !SchematicBlockDefault.hasWashingFluidNeighbour(level, blockPos, def.blockState,
                neighbour -> !permanent.test(neighbour));
    }

    /**
     * REPLACE: will the fluid at {@code blockPos} stay where it is as far as this Builder is concerned? REPLACE never
     * breaks fluid, and displaces it only by building a block into its cell. So it stays when the cell is outside the
     * box, accepted ({@code CORRECT}: a schematic-air cell holding fluid, a block the schematic wants wet, a fragile
     * cell accepted here), or still to receive a FRAGILE block, which cannot be placed into fluid that washes it
     * either. Counting that last case is what lets two flooded fragile cells beside each other be accepted at all:
     * each washes the other, and neither would ever be accepted first. Any other cell still to build or break (a solid
     * block going into a source) may take the fluid away, so the fragile cell beside it waits for that.
     *
     * <p>Like CLEAR's acceptance, this lasts only while the fluid does: once it drains (its feeder is gone, or a
     * fragile neighbour was built into a source after all), the next check sweep sends the cell back to
     * {@code TO_PLACE} and the block is built.
     */
    private boolean isStayingUnderReplace(BlockPos blockPos) {
        if (isUnmoppable(blockPos)) return true;
        return checkResults[posToIndex(blockPos)] == CHECK_RESULT_TO_PLACE && isFragileSchematicAt(blockPos);
    }

    /**
     * Would vanilla make {@code blockPos} a source of {@code type} again right after the Builder clears it, from
     * fluid the Builder will never remove? For water that is also what re-waterlogs a waterloggable block there.
     * Without this exemption CLEAR's strict {@code isBuilt} sends such a block round the break, place, re-flood loop
     * for ever (an MJ drain that also keeps the whole base position from finishing).
     *
     * <p>Vanilla makes a source only through the infinite-source rule ({@code FlowingFluid#getNewLiquid}): a cell
     * with two or more horizontal SOURCE neighbours of the fluid that may convert ({@code canConvertToSource}: the
     * {@code water_source_conversion} / {@code lava_source_conversion} gamerules, or a modded fluid type's own
     * setting), over a solid or source floor, becomes a source itself; a single source or flowing fluid is held back.
     * So the cause can be read straight off the neighbours, no attempt counting or flow tracing needed: count the
     * horizontal sources the Builder cannot clear ({@link #isUnmoppable}). Two such sources re-make the cell; one
     * cannot. The floor half of the rule is mirrored too: with air (or flowing fluid, or a source the Builder will
     * mop) below, vanilla gives the cell only flowing fluid, which never waterlogs a block, so a dried block stays
     * dry and the strict CLEAR rule applies.
     */
    private boolean isResourcedByUnmoppableFluid(BlockPos blockPos, Fluid type) {
        if (!(tile.getWorldBC() instanceof ServerLevel level) || type == Fluids.EMPTY) return false;
        int sources = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = blockPos.relative(direction);
            FluidState fluid = level.getFluidState(neighbour);
            if (!fluid.isSource() || !fluid.getType().isSame(type)) continue;
            if (isUnmoppable(neighbour) && fluid.canConvertToSource(level, neighbour)) sources++;
        }
        return sources >= 2 && supportsInfiniteSource(blockPos.below(), type);
    }

    /**
     * The floor half of vanilla's infinite-source rule ({@code FlowingFluid#getNewLiquid}): a solid block, or a source
     * of {@code type} the Builder will never remove ({@link #isUnmoppable}; one it will mop stops supporting the cell
     * once mopped, and the next check sweep then dries the block).
     */
    private boolean supportsInfiniteSource(BlockPos floor, Fluid type) {
        BlockState state = tile.getWorldBC().getBlockState(floor);
        // The legacy isSolid() is deprecated, but it is exactly the floor test getNewLiquid applies on every node
        // (1.21.1 through 26.2 all still call belowState.isSolid() there), and no non-deprecated predicate matches
        // it: isFaceSturdy(UP) rejects a bottom slab that isSolid accepts, and blocksMotion() is deprecated too (it
        // is isSolid minus cobweb and bamboo sapling). Matching vanilla's re-source rule is the point, so keep
        // vanilla's own test (as TilePump does).
        @SuppressWarnings("deprecation")
        boolean solid = state.isSolid();
        if (solid) return true;
        FluidState fluid = state.getFluidState();
        return fluid.isSource() && fluid.getType().isSame(type) && isUnmoppable(floor);
    }

    /**
     * Fluid at {@code blockPos} that the Builder will never remove: the cell lies outside the build box (never
     * mopped), or it is a cell this builder already accepted ({@code CORRECT}: a block the schematic wants wet, a
     * wet block tolerated by {@link #isResourcedByUnmoppableFluid}, or an air cell tolerated by
     * {@link #isToleratedFluidAt}). Counting accepted cells is what lets a row of cells flooding each other along
     * a shore settle instead of looping one cell further on.
     *
     * <p>Every in-box cell, schematic air included, is read off {@code checkResults} rather than re-derived from the
     * world: re-deriving an air cell would call back into {@link #isToleratedFluidAt}, which asks this of its own
     * neighbours, and two air cells re-made into sources by each other would recurse without end. An air cell whose
     * {@code CORRECT} predates fluid flowing into it can vouch for a neighbour for at most one sweep: the sweep
     * re-checks it (to {@code TO_BREAK} if that fluid is moppable), and the neighbour on its next check.
     */
    private boolean isUnmoppable(BlockPos blockPos) {
        return !getBuildingInfo().box.contains(blockPos)
            || checkResults[posToIndex(blockPos)] == CHECK_RESULT_CORRECT;
    }

    /**
     * Should the fluid in this schematic-air cell be left alone?
     *
     * <p>REPLACE and NO_REPLACE never remove fluid (the break queue refuses every fluid cell outside CLEAR), so a
     * cell holding nothing but fluid is as clear as those modes ever make it, and counts as done; otherwise it stayed
     * "to break" for ever and the build never finished. A bubble column counts as fluid too
     * ({@link #isFluidOnly}): breaking it only leaves water that the floor turns back into one. A real block holding
     * fluid (a waterlogged fence, seagrass, kelp) is not covered: it is still a block the schematic does not want,
     * so it is broken (those modes' break queue accepts it) and the fluid it leaves then counts as done.
     *
     * <p>CLEAR: is the fluid in this cell refilled at once, whenever the Builder mops it, from fluid it can never
     * remove? A tolerated wet block (or fluid just outside the box) is a permanent source, so vanilla keeps spreading
     * it into the air cells beside and below it, and each mop's neighbour update only reschedules that flow. Mopping
     * such a cell is a forever loop, so it counts as clear. Any fluid qualifies, water, lava or modded, but only in
     * the two ways vanilla fills a cell ({@code FlowingFluid#getNewLiquid}): a SOURCE made by that fluid's
     * infinite-source rule ({@link #isResourcedByUnmoppableFluid}), or FLOWING fluid fed by an unmoppable neighbour
     * of the same fluid: directly above it, or a horizontal neighbour holding more of it than this cell does.
     * Requiring strictly more sideways and allowing feeding only from above means tolerated flowing cells can never
     * vouch for each other in a circle; every chain ends at fluid that really is permanent. Fluid fed by a source the
     * Builder will still mop is not tolerated, so it is mopped as before (and drains once its source is gone). A
     * bubble column counts here as the water source it stands in: the infinite-source rule re-makes that water, and
     * the soul sand or magma below turns it straight back into a column, so mopping it loops just as for still water.
     */
    @Override
    protected boolean isToleratedFluidAt(BlockPos blockPos) {
        if (getBuildingInfo() == null) return false;
        BlockState state = tile.getWorldBC().getBlockState(blockPos);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) return false;
        if (!isFluidOnly(state)) return false;
        if (tile.getFluidMode() != EnumFluidHandlingMode.CLEAR) return true;
        Fluid type = fluid.getType();
        if (fluid.isSource()) return isResourcedByUnmoppableFluid(blockPos, type);
        BlockPos above = blockPos.above();
        if (tile.getWorldBC().getFluidState(above).getType().isSame(type) && isUnmoppable(above)) return true;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = blockPos.relative(direction);
            FluidState feeder = tile.getWorldBC().getFluidState(neighbour);
            if (feeder.getType().isSame(type) && feeder.getAmount() > fluid.getAmount() && isUnmoppable(neighbour)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected PlaceResult doPlaceTask(PlaceTask placeTask) {
        // noinspection ConstantConditions
        if (getBuildingInfo() == null) return PlaceResult.FAILED;
        ISchematicBlock schematic = getSchematicBlock(placeTask.pos);
        if (schematic == null) return PlaceResult.FAILED;
        // A schematic sets its block down directly (level.setBlock), so no item use posts the placement events a
        // player's placement does: ask protection mods first, about every cell the block fills (a bed's head too).
        if (tile.getWorldBC() instanceof ServerLevel server) {
            List<BlockPos> cells = schematic instanceof SchematicBlockDefault def
                ? def.placedCells(placeTask.pos)
                : List.of(placeTask.pos);
            if (!cells.isEmpty() && !BlockUtil.canMachinePlace(server, cells, tile.getOwner())) {
                return PlaceResult.REFUSED;
            }
        }
        // Fluid-aware path: SchematicBlockDefault has an overload that waterlogs the placed block
        // or destroys a fluid source at the target position under REPLACE/CLEAR. Third-party
        // ISchematicBlock implementations fall back to the plain build(level, pos), which just
        // won't handle fluids — acceptable because fluid-at-placement is mostly a vanilla
        // blockstate concern. Same overload also takes the container-contents mode so chests/
        // hoppers/etc. place either filled (INCLUDE — current behaviour) or empty (IGNORE).
        if (schematic instanceof SchematicBlockDefault def) {
            boolean includeContents = tile.getContainerContentsMode() != EnumContainerContentsMode.IGNORE;
            return def.build(tile.getWorldBC(), placeTask.pos, tile.getFluidMode(), includeContents)
                ? PlaceResult.PLACED : PlaceResult.FAILED;
        }
        return schematic.build(tile.getWorldBC(), placeTask.pos) ? PlaceResult.PLACED : PlaceResult.FAILED;
    }

    /**
     * May the schematic entity whose world position is {@code entityPos} be spawned? Entities are spawned straight
     * into the level too, so a protection mod is asked about the cell the entity stands in, through the same
     * placement probe as a block; a refused cell is parked with the block refusals (the same question: may this
     * builder place here), so a claimed item frame is not re-asked on every tick of a finished build.
     */
    private boolean mayPlaceEntityAt(Vec3 entityPos) {
        if (!(tile.getWorldBC() instanceof ServerLevel server)) {
            return true;
        }
        BlockPos cell = BlockPos.containing(entityPos);
        if (isPlaceParked(cell)) {
            return false;
        }
        if (BlockUtil.canMachinePlace(server, cell, tile.getOwner())) {
            return true;
        }
        placeRefusals.remember(server, cell, tile.getOwner());
        return false;
    }

    @Override
    public boolean tick() {
        if (tile.getWorldBC().isClientSide()) {
            return super.tick();
        }

        // Find entities within the building area
        List<Entity> entitiesWithinBox = tile.getWorldBC().getEntities(
            (Entity) null,
            getBuildingInfo().box.getBoundingBox(),
            Objects::nonNull
        );

        // Find entities that need spawning
        // ⚡ Bolt: Use standard for loops and distanceToSqr to avoid Stream overhead and square roots
        List<ISchematicEntity> toSpawn = new ArrayList<>();
        double maxDistSq = MAX_ENTITY_DISTANCE * MAX_ENTITY_DISTANCE;
        for (ISchematicEntity schematicEntity : getBuildingInfo().entities) {
            boolean found = false;
            Vec3 targetPos = schematicEntity.getPos().add(Vec3.atLowerCornerOf(getBuildingInfo().offsetPos));
            for (Entity entity : entitiesWithinBox) {
                if (entity.position().distanceToSqr(targetPos) < maxDistSq) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                toSpawn.add(schematicEntity);
            }
        }

        // Compute needed stacks for display
        // ⚡ Bolt: Avoid Streams for concatenating lists
        remainingDisplayRequired.clear();
        List<ItemStack> displayRequiredConcat = new ArrayList<>(remainingDisplayRequiredBlocksConcat);
        for (ISchematicEntity schematicEntity : toSpawn) {
            getDisplayRequired(
                getBuildingInfo().entitiesRequiredItems.get(schematicEntity),
                getBuildingInfo().entitiesRequiredFluids.get(schematicEntity)
            ).forEach(displayRequiredConcat::add);
        }
        remainingDisplayRequired.addAll(StackUtil.mergeSameItems(displayRequiredConcat));

        // The items-only twin: block requirements from afterChecks() plus the to-spawn entities' item
        // costs (no fluid buckets — the robot supply line fetches items, the tanks are pipe-fed).
        remainingRequiredItems.clear();
        List<ItemStack> requiredItemsConcat = new ArrayList<>(remainingRequiredItemBlocksConcat);
        for (ISchematicEntity schematicEntity : toSpawn) {
            List<ItemStack> entityItems = getBuildingInfo().entitiesRequiredItems.get(schematicEntity);
            if (entityItems != null) {
                requiredItemsConcat.addAll(entityItems);
            }
        }
        remainingRequiredItems.addAll(StackUtil.mergeSameItems(requiredItemsConcat));

        // Kill entities that shouldn't be there
        // ⚡ Bolt: Use standard for loops and distanceToSqr to avoid Stream overhead and square roots
        List<Entity> toKill = new ArrayList<>();
        for (Entity entity : entitiesWithinBox) {
            if (entity == null) continue;

            boolean foundClose = false;
            for (ISchematicEntity schematicEntity : getBuildingInfo().entities) {
                Vec3 pos = schematicEntity.getPos().add(Vec3.atLowerCornerOf(getBuildingInfo().offsetPos));
                if (entity.position().distanceToSqr(pos) < maxDistSq) {
                    foundClose = true;
                    break;
                }
            }

            if (!foundClose) {
                if (SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(
                        tile.getWorldBC(),
                        BlockPos.ZERO,
                        entity
                    )) != null) {
                    toKill.add(entity);
                }
            }
        }
        if (!toKill.isEmpty()) {
            if (!tile.getBattery().isFull()) {
                return false;
            } else {
                toKill.forEach(Entity::discard);
            }
        }

        // Call superclass method
        if (super.tick()) {
            boolean entityRefused = false;
            // Spawn needed entities
            if (!toSpawn.isEmpty()) {
                if (!tile.getBattery().isFull()) {
                    return false;
                } else {
                    // ⚡ Bolt: Use standard for loop for entity spawning
                    for (ISchematicEntity schematicEntity : toSpawn) {
                        if (!tryExtractRequired(
                                getBuildingInfo().entitiesRequiredItems.get(schematicEntity),
                                getBuildingInfo().entitiesRequiredFluids.get(schematicEntity),
                                true
                            ).isPresent()) {
                            continue;
                        }
                        if (!mayPlaceEntityAt(
                                schematicEntity.getPos().add(Vec3.atLowerCornerOf(getBuildingInfo().offsetPos)))) {
                            entityRefused = true;
                            continue;
                        }
                        if (schematicEntity.build(tile.getWorldBC(), getBuildingInfo().offsetPos) != null) {
                            tryExtractRequired(
                                getBuildingInfo().entitiesRequiredItems.get(schematicEntity),
                                getBuildingInfo().entitiesRequiredFluids.get(schematicEntity),
                                false
                            );
                        }
                    }
                }
            }
            // A claim-refused entity is still owed, like a parked block: the position is not finished, so a path-fed
            // Builder stays here (moving on would rebuild the building info and drop it) and asks again once the
            // refusal's window ends.
            return !entityRefused;
        } else {
            return false;
        }
    }

    @Override
    protected boolean check(BlockPos blockPos) {
        if (super.check(blockPos)) {
            remainingDisplayRequiredBlocks[posToIndex(blockPos)] =
                checkResults[posToIndex(blockPos)] != CHECK_RESULT_CORRECT
                    ?
                    getDisplayRequired(
                        getBuildingInfo().toPlaceRequiredItems[posToIndex(blockPos)],
                        getBuildingInfo().toPlaceRequiredFluids[posToIndex(blockPos)]
                    ).collect(Collectors.toList())
                    : Collections.emptyList();
            remainingRequiredItemBlocks[posToIndex(blockPos)] =
                checkResults[posToIndex(blockPos)] != CHECK_RESULT_CORRECT
                    ? getRequiredItems(posToIndex(blockPos))
                    : Collections.emptyList();
            return true;
        } else {
            return false;
        }
    }

    /** The items-only requirement list for one position: {@code toPlaceRequiredItems} verbatim (fluid
     *  costs live in their own array and are not the robot supply line's business). */
    private List<ItemStack> getRequiredItems(int index) {
        List<ItemStack> items = getBuildingInfo().toPlaceRequiredItems[index];
        return items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    @Override
    protected void afterChecks() {
        remainingDisplayRequiredBlocksConcat = StackUtil.mergeSameItems(
            Arrays.stream(remainingDisplayRequiredBlocks)
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
        );
        if (remainingRequiredItemBlocks == null) {
            return;
        }
        remainingRequiredItemBlocksConcat = StackUtil.mergeSameItems(
            Arrays.stream(remainingRequiredItemBlocks)
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
        );
    }

    @Override
    public void writeToByteBuf(PacketBufferBC buffer) {
        super.writeToByteBuf(buffer);
        buffer.writeInt(remainingDisplayRequired.size());
        remainingDisplayRequired.forEach(stack -> {
            CompoundTag tag = new CompoundTag();
            if (!stack.isEmpty()) {
                // ItemStack.CODEC clamps count to 1-99; display totals can exceed that, so the
                // item (with components) goes through the codec and the real count is separate.
                ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), stack.copyWithCount(1))
                        .resultOrPartial()
                        .ifPresent(payload -> tag.put("stack", payload));
            }
            buffer.writeNbt(tag);
            buffer.writeInt(stack.getCount());
        });
    }

    @Override
    public void readFromByteBuf(PacketBufferBC buffer) {
        super.readFromByteBuf(buffer);
        remainingDisplayRequired.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> {
            CompoundTag tag = buffer.readNbt();
            Tag payload = tag == null ? null : tag.get("stack");
            ItemStack stack = payload == null
                    ? ItemStack.EMPTY
                    : ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), payload)
                            .resultOrPartial()
                            .orElse(ItemStack.EMPTY);
            int count = buffer.readInt();
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(count);
        }).forEach(remainingDisplayRequired::add);
    }
}
