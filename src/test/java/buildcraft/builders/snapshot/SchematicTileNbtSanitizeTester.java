/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * A blueprint's {@code tileNbt} is written into the placed block entity almost verbatim, so the loot-table keys are
 * a free-loot dispenser: vanilla's chest/barrel/dispenser/crafter loadAdditional read them straight from the tag,
 * and a chest scanned in a generated structure carries them. Deserialization strips them — and nothing else: the
 * machine configuration existing blueprints rely on (a chest's captured items, its custom name) must survive.
 */
public class SchematicTileNbtSanitizeTester {

    private static CompoundTag lootLacedTileNbt() {
        CompoundTag tileNbt = new CompoundTag();
        tileNbt.putString("LootTable", "minecraft:chests/simple_dungeon");
        tileNbt.putLong("LootTableSeed", 42L);
        ListTag items = new ListTag();
        items.add(new CompoundTag());
        tileNbt.put("Items", items);
        tileNbt.putString("CustomName", "My chest");
        CompoundTag components = new CompoundTag();
        components.putString("minecraft:container_loot", "minecraft:chests/simple_dungeon;42");
        components.putString("minecraft:custom_name", "My chest");
        tileNbt.put("components", components);
        return tileNbt;
    }

    @Test
    public void theLootKeysAreStrippedAndEverythingElseSurvives() {
        CompoundTag tileNbt = SchematicBlockDefault.sanitizeTileNbt(lootLacedTileNbt());
        Assertions.assertFalse(tileNbt.contains("LootTable"));
        Assertions.assertFalse(tileNbt.contains("LootTableSeed"));
        Assertions.assertTrue(tileNbt.contains("Items"), "a chest's captured contents are the blueprint's business");
        Assertions.assertTrue(tileNbt.contains("CustomName"));
        CompoundTag components = NBTUtilBC.getCompoundOrNull(tileNbt, "components");
        Assertions.assertNotNull(components);
        Assertions.assertFalse(components.contains("minecraft:container_loot"));
        Assertions.assertTrue(components.contains("minecraft:custom_name"), "cosmetic components survive");
    }

    @Test
    public void aTileNbtWithNoLootKeysIsUntouched() {
        CompoundTag tileNbt = lootLacedTileNbt();
        tileNbt.remove("LootTable");
        tileNbt.remove("LootTableSeed");
        NBTUtilBC.getCompoundOrNull(tileNbt, "components").remove("minecraft:container_loot");
        CompoundTag before = tileNbt.copy();
        Assertions.assertSame(tileNbt, SchematicBlockDefault.sanitizeTileNbt(tileNbt));
        Assertions.assertEquals(before, tileNbt);
    }

    @Test
    public void nullStaysNull() {
        Assertions.assertNull(SchematicBlockDefault.sanitizeTileNbt(null));
    }

    @Test
    public void aDeserializedSchematicCarriesNoLootKeys() throws InvalidInputDataException {
        // The choke point itself: a serialized schematic whose tileNbt was laced reads back clean.
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = Blocks.CHEST.defaultBlockState();
        schematic.placeBlock = Blocks.CHEST;
        schematic.tileRotation = Rotation.NONE;
        schematic.tileNbt = lootLacedTileNbt();
        CompoundTag nbt = schematic.serializeNbt();
        CompoundTag serializedTileNbt = NBTUtilBC.getCompoundOrNull(nbt, "tileNbt");
        Assertions.assertNotNull(serializedTileNbt);
        Assertions.assertTrue(serializedTileNbt.contains("LootTable"),
            "the fixture must actually serialize the loot keys for this test to mean anything");

        SchematicBlockDefault read = new SchematicBlockDefault();
        read.deserializeNbt(nbt);
        Assertions.assertNotNull(read.tileNbt);
        Assertions.assertFalse(read.tileNbt.contains("LootTable"));
        Assertions.assertFalse(read.tileNbt.contains("LootTableSeed"));
        Assertions.assertTrue(read.tileNbt.contains("Items"), "the captured contents survive the read");
        Assertions.assertTrue(read.tileNbt.contains("CustomName"));
    }
}
