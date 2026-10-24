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
    public void beesAreStrippedOnlyFromTheirOwnBlocks() {
        // Nothing can price a hive's bees (entities), so building them from the captured tag would mint them; a chest
        // that happens to carry the same key name keeps it. A pot's sherds are not stripped: its rule prices them
        // (SchematicDecoratedPotTester).
        CompoundTag hive = new CompoundTag();
        hive.put("bees", new ListTag());
        SchematicBlockDefault.sanitizeTileNbt(hive, Blocks.BEE_NEST.defaultBlockState());
        Assertions.assertFalse(hive.contains("bees"));

        CompoundTag pot = new CompoundTag();
        pot.put("sherds", new ListTag());
        pot.put("item", new CompoundTag());
        SchematicBlockDefault.sanitizeTileNbt(pot, Blocks.DECORATED_POT.defaultBlockState());
        Assertions.assertTrue(pot.contains("sherds"), "the pot's sides are priced by its rule, so they stay");
        Assertions.assertTrue(pot.contains("item"), "the pot's stored item is priced by its rule, so it stays");

        CompoundTag chest = new CompoundTag();
        chest.put("bees", new ListTag());
        chest.put("sherds", new ListTag());
        SchematicBlockDefault.sanitizeTileNbt(chest, Blocks.CHEST.defaultBlockState());
        Assertions.assertTrue(chest.contains("bees") && chest.contains("sherds"));
    }

    @Test
    public void itemHoldersTheBuildClearCannotReachLoseTheirItems() {
        // Not Clearable, so the deny-by-default clear in build() never empties them: a brushed suspicious block's
        // item (brushing the rebuilt block would yield it).
        CompoundTag sand = new CompoundTag();
        sand.put("item", new CompoundTag());
        SchematicBlockDefault.sanitizeTileNbt(sand, Blocks.SUSPICIOUS_SAND.defaultBlockState());
        Assertions.assertFalse(sand.contains("item"));

        // A vault's config picks its reward table and key item, and its server data queues rewards; a trial
        // spawner's data and configs pick the players owed a reward, the loot tables and the entities it spawns.
        // All of it goes, on every node's layout.
        CompoundTag vaultConfig = new CompoundTag();
        vaultConfig.putString("loot_table", "minecraft:chests/end_city_treasure");
        CompoundTag serverData = new CompoundTag();
        serverData.put("items_to_eject", new ListTag());
        CompoundTag vault = new CompoundTag();
        vault.put("config", vaultConfig);
        vault.put("server_data", serverData);
        CompoundTag trialSpawner = new CompoundTag();
        trialSpawner.put("registered_players", new ListTag());
        trialSpawner.putString("ejecting_loot_table", "minecraft:chests/end_city_treasure");
        trialSpawner.put("normal_config", new CompoundTag());
        for (var entry : java.util.Map.of(vault, Blocks.VAULT, trialSpawner, Blocks.TRIAL_SPAWNER).entrySet()) {
            CompoundTag sanitized =
                SchematicBlockDefault.sanitizeTileNbt(entry.getKey(), entry.getValue().defaultBlockState());
            Assertions.assertNotNull(sanitized, entry.getValue().toString());
            Assertions.assertTrue(sanitized.isEmpty(), entry.getValue() + " keeps " + sanitized);
        }

        // A Builder's queued place tasks carry the items already taken for them.
        CompoundTag builder = new CompoundTag();
        builder.put("builderState", new CompoundTag());
        builder.put("builderClientData", new CompoundTag());
        builder.putInt("fluidMode", 1);
        SchematicBlockDefault.sanitizeTileNbt(builder, buildcraft.builders.BCBuildersBlocks.BUILDER.get().defaultBlockState());
        Assertions.assertFalse(builder.contains("builderState") || builder.contains("builderClientData"));
        Assertions.assertTrue(builder.contains("fluidMode"), "the Builder's configuration stays");
    }

    /** A spawn entry as both nodes' {@code SpawnData} codec writes it: an entity carrying gear, plus an equipment
     *  loot table. */
    private static CompoundTag gearedZombieSpawnData() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        ListTag hands = new ListTag();
        hands.add(new CompoundTag());
        entity.put("HandItems", hands);
        CompoundTag equipment = new CompoundTag();
        equipment.putString("loot_table", "minecraft:equipment/trial_chamber");
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        spawnData.put("equipment", equipment);
        return spawnData;
    }

    @Test
    public void aMobSpawnerKeepsItsMobTypeAndNothingElseOfItsSpawns() {
        // The mob type is what a scanned spawner is for; the entity's own data (gear, or an item entity's stack) and
        // an equipment loot table are what nothing prices.
        CompoundTag spawner = new CompoundTag();
        spawner.putShort("Delay", (short) 20);
        spawner.put("SpawnData", gearedZombieSpawnData());
        CompoundTag weighted = new CompoundTag();
        weighted.put("data", gearedZombieSpawnData());
        weighted.putInt("weight", 1);
        ListTag potentials = new ListTag();
        potentials.add(weighted);
        spawner.put("SpawnPotentials", potentials);

        CompoundTag sanitized = SchematicBlockDefault.sanitizeTileNbt(spawner, Blocks.SPAWNER.defaultBlockState());
        CompoundTag next = NBTUtilBC.getCompoundOrNull(sanitized, "SpawnData");
        Assertions.assertNotNull(next, "the next spawn stays");
        Assertions.assertFalse(next.contains("equipment"), "an equipment loot table is not the blueprint's to place");
        CompoundTag entity = NBTUtilBC.getCompoundOrNull(next, "entity");
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(1, entity.size(), "only the mob type is left of the entity, got " + entity);
        Assertions.assertEquals("minecraft:zombie", NBTUtilBC.getString(entity, "id", ""));

        ListTag keptPotentials = NBTUtilBC.getList(sanitized, "SpawnPotentials", net.minecraft.nbt.Tag.TAG_COMPOUND);
        Assertions.assertEquals(1, keptPotentials.size(), "the weighted spawns stay");
        CompoundTag keptData = NBTUtilBC.getCompoundOrNull(NBTUtilBC.getCompoundOrNull(keptPotentials, 0), "data");
        Assertions.assertNotNull(keptData);
        Assertions.assertFalse(keptData.contains("equipment"));
        Assertions.assertEquals(1, NBTUtilBC.getCompoundOrNull(keptData, "entity").size());
        Assertions.assertTrue(sanitized.contains("Delay"), "timings are harmless and stay");
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
