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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;

import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.net.PacketBufferBC;

public class BlueprintBuilder extends SnapshotBuilder<ITileForBlueprintBuilder> {
    private static final double MAX_ENTITY_DISTANCE = 0.1D;
    private static final String FLUID_STACK_KEY = "BuilderFluidStack";

    private List<ItemStack>[] remainingDisplayRequiredBlocks;
    private List<ItemStack> remainingDisplayRequiredBlocksConcat = Collections.emptyList();
    public List<ItemStack> remainingDisplayRequired = new ArrayList<>();
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
    }

    @Override
    public void resourcesChanged() {
        super.resourcesChanged();
        extractRequiredCache.clear();
    }

    @Override
    public void cancel() {
        super.cancel();
        remainingDisplayRequiredBlocks = null;
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
                            try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                                int extracted = tile.getTankManager().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(stack), stack.getAmount(), tx);
                                return extracted == stack.getAmount();
                            }
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
                                    try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                                        int extracted = tile.getTankManager().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluidStack), fluidStack.getAmount(), tx);
                                        if (!simulate) tx.commit();
                                        return new FluidStack(fluidStack.getFluid(), extracted);
                                    }
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

    /**
     * The "dry an existing waterlogged block" operation is item-free: the user already has the
     * block they want at the position, the only thing changing is its WATERLOGGED property. No
     * fence/glass-pane/whatever needs to come out of inventory just to toggle a flag. Detected
     * via the schematic's own {@link SchematicBlockDefault#isWaterlogClearOnly} helper which
     * checks block-type match + world-wet/schematic-dry under CLEAR mode.
     */
    private boolean isWaterlogClearOnlyAt(BlockPos blockPos) {
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (!(schematic instanceof SchematicBlockDefault def)) return false;
        return def.isWaterlogClearOnly(tile.getWorldBC(), blockPos, tile.getFluidMode());
    }

    @Override
    protected boolean isAllowedDuringFluidMop(BlockPos blockPos) {
        // Waterlog-clear-only positions ARE part of mopping — drying a waterlogged block stops
        // it from emitting water to neighbours. Without this carve-out, a build area with
        // waterlogged source-emitting blocks (e.g. waterlogged stairs feeding a corner) would
        // deadlock: corner break tasks fire forever as the stairs keep emitting, and the
        // dry-the-stairs place tasks sit deferred behind the area-fluid-clear gate.
        return isWaterlogClearOnlyAt(blockPos);
    }

    @Override
    protected boolean isFragileSchematicAt(BlockPos blockPos) {
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (!(schematic instanceof SchematicBlockDefault def)) return false;
        if (def.blockState == null) return false;
        // canBeReplaced is fluid-type-aware; passing WATER as the representative fluid is fine
        // because every block whose canBeReplaced returns true for water (snow_layer, carpet,
        // button, redstone wire, sapling, torch, sign, lever, …) is exactly the set of fragile
        // blocks that can be displaced by fluid flow. The fluid-type sensitivity matters for
        // LiquidBlocks-replacing-LiquidBlocks edge cases that don't apply here.
        return def.blockState.canBeReplaced(net.minecraft.world.level.material.Fluids.WATER);
    }

    @Override
    protected boolean hasEnoughToPlaceItems(BlockPos blockPos) {
        if (isWaterlogClearOnlyAt(blockPos)) return true;
        return tryExtractRequired(
            getBuildingInfo().toPlaceRequiredItems[posToIndex(blockPos)],
            getBuildingInfo().toPlaceRequiredFluids[posToIndex(blockPos)],
            true
        ).isPresent();
    }

    @Override
    protected List<ItemStack> getToPlaceItems(BlockPos blockPos) {
        if (isWaterlogClearOnlyAt(blockPos)) return Collections.emptyList();
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
                CompoundTag fluidTag = customData.copyTag().getCompoundOrEmpty(FLUID_STACK_KEY);
                if (fluidTag.isEmpty()) return FluidStack.EMPTY;
                String fluidIdStr = fluidTag.getString("fluid").orElse("");
                int amount = fluidTag.getInt("amount").orElse(0);
                if (fluidIdStr.isEmpty() || amount <= 0) return FluidStack.EMPTY;
                net.minecraft.resources.Identifier id =
                    net.minecraft.resources.Identifier.tryParse(fluidIdStr);
                if (id == null) return FluidStack.EMPTY;
                net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
                if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return FluidStack.EMPTY;
                return new FluidStack(fluid, amount);
            })
            // Defensive filter — if for any reason the round-trip above came back empty (old
            // save data from before this serialization existed, a fluid whose id has since
            // been unregistered, …) skip it rather than crashing tankManager.insert on
            // FluidResource.EMPTY.
            .filter(fluidStack -> !fluidStack.isEmpty() && fluidStack.getAmount() > 0)
            .forEach(fluidStack -> {
                try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    tile.getTankManager().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluidStack), fluidStack.getAmount(), tx);
                    tx.commit();
                }
            });
    }

    @Override
    protected boolean isBlockCorrect(BlockPos blockPos) {
        // noinspection ConstantConditions
        if (getBuildingInfo() == null) return false;
        ISchematicBlock schematic = getSchematicBlock(blockPos);
        if (schematic == null) return false;
        // Pass fluidMode through to the SchematicBlockDefault overload so CLEAR mode reports
        // "world wet, schematic dry" as NOT built (i.e. queue it for drying). REPLACE/NO_REPLACE
        // keep the lenient comparison via the no-mode default. Other ISchematicBlock impls
        // (SchematicBlockFluid, SchematicBlockAir) don't care about waterlogging mode.
        if (schematic instanceof SchematicBlockDefault def) {
            return def.isBuilt(tile.getWorldBC(), blockPos, tile.getFluidMode());
        }
        return schematic.isBuilt(tile.getWorldBC(), blockPos);
    }

    @Override
    protected boolean doPlaceTask(PlaceTask placeTask) {
        // noinspection ConstantConditions
        if (getBuildingInfo() == null) return false;
        ISchematicBlock schematic = getSchematicBlock(placeTask.pos);
        if (schematic == null) return false;
        // Fluid-aware path: SchematicBlockDefault has an overload that waterlogs the placed block
        // or destroys a fluid source at the target position under REPLACE/CLEAR. Third-party
        // ISchematicBlock implementations fall back to the plain build(level, pos), which just
        // won't handle fluids — acceptable because fluid-at-placement is mostly a vanilla
        // blockstate concern.
        if (schematic instanceof SchematicBlockDefault def) {
            return def.build(tile.getWorldBC(), placeTask.pos, tile.getFluidMode());
        }
        return schematic.build(tile.getWorldBC(), placeTask.pos);
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
            // Spawn needed entities
            if (!toSpawn.isEmpty()) {
                if (!tile.getBattery().isFull()) {
                    return false;
                } else {
                    // ⚡ Bolt: Use standard for loop for entity spawning
                    for (ISchematicEntity schematicEntity : toSpawn) {
                        if (tryExtractRequired(
                                getBuildingInfo().entitiesRequiredItems.get(schematicEntity),
                                getBuildingInfo().entitiesRequiredFluids.get(schematicEntity),
                                true
                            ).isPresent()) {
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
            }
            return true;
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
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void afterChecks() {
        remainingDisplayRequiredBlocksConcat = StackUtil.mergeSameItems(
            Arrays.stream(remainingDisplayRequiredBlocks)
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
