/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("BCBlueprint");

    public final List<ISchematicBlock> palette = new ArrayList<>();
    public int[] data;
    public final List<ISchematicEntity> entities = new ArrayList<>();
    /** Whether {@link #deserializeNBT} dropped a palette entry or an entity its gates refuse. Such a blueprint is not
     *  {@linkplain #readFully() read fully}: the store must never write the reduced copy over the file. */
    private boolean droppedOnRead;

    /** How many of the ids a drop warning names, and how long each may be: the ids come from untrusted bytes. */
    private static final int MAX_LOGGED_IDS = 5;
    private static final int MAX_LOGGED_ID_LENGTH = 64;

    @Override
    public Blueprint copy() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.facing = facing;
        blueprint.offset = offset;
        blueprint.palette.addAll(palette);
        blueprint.data = data.clone();
        blueprint.entities.addAll(entities);
        // A copy of a reduced blueprint is just as reduced: whatever it is checked for, it must not pass as full.
        blueprint.droppedOnRead = droppedOnRead;
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
        droppedOnRead = false;
        int replacedBlocks = 0;
        int droppedEntities = 0;
        Set<String> droppedIds = new LinkedHashSet<>();
        for (CompoundTag schematicBlockTag :
            NBTUtilBC.readCompoundList(nbt.get("palette")).collect(Collectors.toList())) {
            // A legacy default-typed pipe is re-read as the pipe schematic (see upgradeLegacyPipe).
            ISchematicBlock block = SchematicBlockManager.upgradeLegacyPipe(
                SchematicBlockManager.readFromNBT(schematicBlockTag), schematicBlockTag);
            // The Architect's capture gate, re-run on the read: an upload's palette can name any block, and one no
            // scan would capture is reduced to air at the same index, so data[] still resolves. Dropped rather than
            // refused, like an entity below, so a modpack change cannot brick a stored blueprint.
            String refusedId = SchematicBlockManager.captureGateRefusal(block);
            if (refusedId != null) {
                replacedBlocks++;
                noteDroppedId(droppedIds, refusedId);
                block = new SchematicBlockAir();
            }
            palette.add(block);
        }
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
        // The size is untrusted (an upload's header can claim any size in a few bytes): the cells are allocated only
        // once the list actually holds that many, so the array never outgrows the NBT that was read.
        int expectedLength = Snapshot.checkedDataSize(size);
        if (serializedDataLength != expectedLength) {
            throw new InvalidInputDataException(
                "Serialized data has length of " + serializedDataLength +
                    ", but we expected " +
                    expectedLength + " (" + size.toString() + ")"
            );
        }
        data = new int[expectedLength];
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
        // Every cell indexes the palette, and the cells come from the same untrusted bytes as the palette itself: an
        // out-of-range index would throw on the first palette lookup of the builder tick (BuildingInfo walks
        // data[] directly), crash-looping it. Refuse the blueprint here instead — an upload is refused to its sender
        // by the library, a planted file is skipped by the store — the same treatment a bad length gets above.
        // (countNonAirCells/countMatchingCells already skip such cells defensively; this keeps one from existing.)
        for (int idx = 0; idx < data.length; idx++) {
            if (data[idx] < 0 || data[idx] >= palette.size()) {
                throw new InvalidInputDataException(
                    "Blueprint cell " + idx + " holds palette index " + data[idx] +
                        ", but the palette holds " + palette.size() + " entries"
                );
            }
        }
        entities.clear();
        for (CompoundTag schematicEntityTag :
            NBTUtilBC.readCompoundList(nbt.get("entities")).collect(Collectors.toList())) {
            ISchematicEntity entity = SchematicEntityManager.readFromNBT(schematicEntityTag);
            // Deny-by-default entity gate: an entity no JSON rule sanctions would build for free, so it is dropped
            // here rather than refused whole-file — one unruled entity (e.g. a modpack removing a rule) must not
            // brick every stored blueprint that happens to contain it. build() can then never see it.
            if (entity instanceof SchematicEntityDefault schematicEntity && !SchematicEntityDefault.isPricedByRules(schematicEntity.entityNbt)) {
                droppedEntities++;
                noteDroppedId(droppedIds, NBTUtilBC.getString(schematicEntity.entityNbt, "id", ""));
                continue;
            }
            entities.add(entity);
        }
        if (replacedBlocks > 0 || droppedEntities > 0) {
            droppedOnRead = true;
            // One line per blueprint, however many entries it drops: a planted or uploaded file must not flood the log.
            LOGGER.warn(
                "Read a blueprint with content no schematic rule sanctions: {} palette entr{} replaced with air, {} "
                    + "entit{} dropped (ids: {})",
                replacedBlocks, replacedBlocks == 1 ? "y" : "ies", droppedEntities, droppedEntities == 1 ? "y" : "ies",
                droppedIds
            );
        }
    }

    /** Remembers {@code id} for the drop warning: distinct, at most {@link #MAX_LOGGED_IDS}, each made log-safe. */
    private static void noteDroppedId(Set<String> ids, String id) {
        if (ids.size() >= MAX_LOGGED_IDS) return;
        ids.add(logSafe(id, MAX_LOGGED_ID_LENGTH));
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

    /** Every palette entry and entity must vouch for its read. BuildCraft's own schematics can: air holds nothing,
     *  the block and fluid schematics check every block they name, and the default entity schematic keeps the
     *  entity's NBT as it was saved. A schematic an addon registered cannot say, so it counts as not read fully, and
     *  neither does a blueprint the read gates reduced (a palette entry turned to air, an entity dropped). */
    @Override
    boolean readFully() {
        if (droppedOnRead) return false;
        for (ISchematicBlock block : palette) {
            boolean full = block instanceof SchematicBlockAir
                || block instanceof SchematicBlockDefault schematic && schematic.readFully()
                || block instanceof SchematicBlockFluid fluid && fluid.readFully();
            if (!full) return false;
        }
        for (ISchematicEntity entity : entities) {
            if (!(entity instanceof SchematicEntityDefault)) return false;
        }
        return true;
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
