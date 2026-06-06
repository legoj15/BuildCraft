/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.ISchematicEntity;

import buildcraft.lib.misc.NBTUtilBC;

@SuppressWarnings("this-escape")
public class Blueprint extends Snapshot {
    public final List<ISchematicBlock> palette = new ArrayList<>();
    public int[] data;
    public final List<ISchematicEntity> entities = new ArrayList<>();

    @Override
    public Blueprint copy() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.facing = facing;
        blueprint.offset = offset;
        blueprint.palette.addAll(palette);
        blueprint.data = data.clone();
        blueprint.entities.addAll(entities);
        blueprint.computeKey();
        return blueprint;
    }

    /**
     * Swap every palette entry that semantically matches {@code from} with {@code to}.
     * <p>
     * Matching is delegated to {@link #schematicsMatch(ISchematicBlock, ISchematicBlock)}:
     * we compare {@code blockState} and {@code placeBlock} on {@link SchematicBlockDefault}
     * rather than full {@code .equals()}, because freshly-captured single-block schematics
     * carry scan-context annotations ({@code requiredBlockOffsets}, {@code ignoredProperties})
     * that almost never line up with the palette entries the Architect serialized during its
     * own scan. Whole-object equality would virtually never match — which is exactly the
     * 1.12.2 bug (issue: "schematics get consumed, blueprint unchanged").
     * <p>
     * Palette-index swaps are done in place, so {@code data[]} needs no reallocation.
     * If {@code to} already exists elsewhere in the palette this leaves a harmless duplicate;
     * the blueprint still renders and builds identically. Deduplication is out of scope here.
     */
    public void replace(ISchematicBlock from, ISchematicBlock to) {
        if (from == null || to == null) {
            return;
        }
        for (int i = 0; i < palette.size(); i++) {
            if (schematicsMatch(palette.get(i), from)) {
                palette.set(i, to);
            }
        }
    }

    /** Count of palette entries matching {@code from}. Cheap — used for GUI feedback. */
    public int countMatchingPaletteEntries(ISchematicBlock from) {
        if (from == null) {
            return 0;
        }
        int n = 0;
        for (ISchematicBlock entry : palette) {
            if (schematicsMatch(entry, from)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Count of {@code data[]} cells whose palette entry matches {@code from}. This is the
     * "how many blocks would actually be replaced" number shown in the GUI summary.
     */
    public int countMatchingCells(ISchematicBlock from) {
        if (from == null || data == null) {
            return 0;
        }
        // Precompute the matching palette indices so we walk data[] with a simple set contains.
        java.util.BitSet matchingIndices = new java.util.BitSet(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            if (schematicsMatch(palette.get(i), from)) {
                matchingIndices.set(i);
            }
        }
        if (matchingIndices.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int cell : data) {
            if (cell >= 0 && cell < palette.size() && matchingIndices.get(cell)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Semantic "same block" test for palette matching. Looser than {@code .equals()} on
     * {@link SchematicBlockDefault}: compares only {@code blockState} and {@code placeBlock},
     * ignoring scan-context fields like {@code requiredBlockOffsets} / {@code ignoredProperties}
     * so that a hand-captured single-block schematic matches the palette entry the Architect
     * captured in a different context. For non-{@code SchematicBlockDefault} implementations
     * we fall back to normal equality, preserving whatever identity they defined.
     */
    private static boolean schematicsMatch(ISchematicBlock a, ISchematicBlock b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof SchematicBlockDefault ad && b instanceof SchematicBlockDefault bd) {
            return java.util.Objects.equals(ad.blockState, bd.blockState)
                && java.util.Objects.equals(ad.placeBlock, bd.placeBlock);
        }
        return a.equals(b);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = super.serializeNBT();
        nbt.put("palette", NBTUtilBC.writeCompoundList(palette.stream().map(SchematicBlockManager::writeToNBT)));
        ListTag list = new ListTag();
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    list.add(IntTag.valueOf(data[posToIndex(x, y, z)]));
                }
            }
        }
        nbt.put("data", list);
        nbt.put("entities", NBTUtilBC.writeCompoundList(entities.stream().map(SchematicEntityManager::writeToNBT)));
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException {
        super.deserializeNBT(nbt);
        palette.clear();
        for (CompoundTag schematicBlockTag :
            NBTUtilBC.readCompoundList(nbt.get("palette")).collect(Collectors.toList())) {
            palette.add(SchematicBlockManager.readFromNBT(schematicBlockTag));
        }
        data = new int[Snapshot.getDataSize(size)];

        // Support both ListTag and IntArray for data
        Tag dataTag = nbt.get("data");
        ListTag serializedDataList = dataTag instanceof ListTag lt ? lt : null;
        int[] serializedDataIntArray = NBTUtilBC.getIntArray(nbt, "data", null);

        if (serializedDataIntArray == null && serializedDataList == null) {
            throw new InvalidInputDataException("Can't read a blueprint with no data!");
        }
        int serializedDataLength = serializedDataList == null
            ? serializedDataIntArray.length
            : serializedDataList.size();
        if (serializedDataLength != getDataSize()) {
            throw new InvalidInputDataException(
                "Serialized data has length of " + serializedDataLength +
                    ", but we expected " +
                    getDataSize() + " (" + size.toString() + ")"
            );
        }
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    int idx = posToIndex(x, y, z);
                    if (serializedDataList != null) {
                        Tag element = serializedDataList.get(idx);
                        //? if >=1.21.10 {
                        data[idx] = element instanceof IntTag it ? it.value() : 0;
                        //?} else {
                        /*data[idx] = element instanceof IntTag it ? it.getAsInt() : 0;*/
                        //?}
                    } else {
                        data[idx] = serializedDataIntArray[idx];
                    }
                }
            }
        }
        entities.clear();
        for (CompoundTag schematicEntityTag :
            NBTUtilBC.readCompoundList(nbt.get("entities")).collect(Collectors.toList())) {
            entities.add(SchematicEntityManager.readFromNBT(schematicEntityTag));
        }
    }

    @Override
    public EnumSnapshotType getType() {
        return EnumSnapshotType.BLUEPRINT;
    }

    /** Walks {@code data[]}, counting cells whose palette entry isn't {@link SchematicBlockAir}.
     *  Precomputes an "is air?" bitset over the palette so each cell is a single bitset lookup
     *  instead of a virtual call — relevant for large blueprints with small palettes. */
    @Override
    public int countNonAirCells() {
        if (data == null || palette.isEmpty()) {
            return 0;
        }
        java.util.BitSet airPaletteIndices = new java.util.BitSet(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            ISchematicBlock entry = palette.get(i);
            if (entry != null && entry.isAir()) {
                airPaletteIndices.set(i);
            }
        }
        int n = 0;
        for (int cell : data) {
            if (cell < 0 || cell >= palette.size()) {
                continue;
            }
            if (!airPaletteIndices.get(cell)) {
                n++;
            }
        }
        return n;
    }

    @SuppressWarnings("WeakerAccess")
    public class BuildingInfo extends Snapshot.BuildingInfo {
        public final List<ItemStack>[] toPlaceRequiredItems;
        public final List<FluidStack>[] toPlaceRequiredFluids;
        public final List<ISchematicBlock> rotatedPalette;
        public final Set<ISchematicEntity> entities;
        public final Map<ISchematicEntity, List<ItemStack>> entitiesRequiredItems;
        public final Map<ISchematicEntity, List<FluidStack>> entitiesRequiredFluids;

        @SuppressWarnings("unchecked")
        public BuildingInfo(BlockPos basePos, Rotation rotation) {
            super(basePos, rotation);
            toPlaceRequiredItems = (List<ItemStack>[]) new List<?>[getDataSize()];
            toPlaceRequiredFluids = (List<FluidStack>[]) new List<?>[getDataSize()];
            rotatedPalette = ImmutableList.copyOf(
                palette.stream()
                    .map(schematicBlock -> schematicBlock.getRotated(rotation))
                    .collect(Collectors.toList())
            );
            for (int z = 0; z < getSnapshot().size.getZ(); z++) {
                for (int y = 0; y < getSnapshot().size.getY(); y++) {
                    for (int x = 0; x < getSnapshot().size.getX(); x++) {
                        ISchematicBlock schematicBlock = rotatedPalette.get(data[posToIndex(x, y, z)]);
                        if (!schematicBlock.isAir()) {
                            toPlaceRequiredItems[posToIndex(x, y, z)] = schematicBlock.computeRequiredItems();
                            toPlaceRequiredFluids[posToIndex(x, y, z)] = schematicBlock.computeRequiredFluids();
                        }
                    }
                }
            }
            ImmutableSet.Builder<ISchematicEntity> entitiesBuilder = ImmutableSet.builder();
            ImmutableMap.Builder<ISchematicEntity, List<ItemStack>> entitiesRequiredItemsBuilder =
                ImmutableMap.builder();
            ImmutableMap.Builder<ISchematicEntity, List<FluidStack>> entitiesRequiredFluidsBuilder =
                ImmutableMap.builder();
            for (ISchematicEntity schematicEntity : getSnapshot().entities) {
                ISchematicEntity rotatedSchematicEntity = schematicEntity.getRotated(rotation);
                entitiesBuilder.add(rotatedSchematicEntity);
                entitiesRequiredItemsBuilder.put(rotatedSchematicEntity, schematicEntity.computeRequiredItems());
                entitiesRequiredFluidsBuilder.put(rotatedSchematicEntity, schematicEntity.computeRequiredFluids());
            }
            entities = entitiesBuilder.build();
            entitiesRequiredItems = entitiesRequiredItemsBuilder.build();
            entitiesRequiredFluids = entitiesRequiredFluidsBuilder.build();
        }

        @Override
        public Blueprint getSnapshot() {
            return Blueprint.this;
        }

        /**
         * Re-bake {@link #toPlaceRequiredItems} under the new container-contents mode. Called from
         * {@link buildcraft.builders.tile.TileBuilder#cycleContainerContentsMode} whenever the
         * player toggles the button, so the Builder's resource panel and place-task extraction
         * reflect the new cost immediately rather than waiting for a snapshot reload. Fluids are
         * unaffected, so only the items array is rewritten. Non-{@link SchematicBlockDefault}
         * schematics fall through to the parameterless {@code computeRequiredItems()} — their
         * required-items contract doesn't expose the contents-mode hook.
         */
        public void refreshRequiredItemsForContentsMode(EnumContainerContentsMode mode) {
            boolean include = mode != EnumContainerContentsMode.IGNORE;
            for (int z = 0; z < getSnapshot().size.getZ(); z++) {
                for (int y = 0; y < getSnapshot().size.getY(); y++) {
                    for (int x = 0; x < getSnapshot().size.getX(); x++) {
                        int idx = posToIndex(x, y, z);
                        ISchematicBlock schematicBlock = rotatedPalette.get(data[idx]);
                        if (schematicBlock.isAir()) continue;
                        if (schematicBlock instanceof SchematicBlockDefault def) {
                            toPlaceRequiredItems[idx] = def.computeRequiredItems(include);
                        } else {
                            toPlaceRequiredItems[idx] = schematicBlock.computeRequiredItems();
                        }
                    }
                }
            }
        }
    }
}
