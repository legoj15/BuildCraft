/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.silicon.tile.TileProgrammingTable;
import buildcraft.platform.registry.BCRegistrar;

public class BCSiliconBlockEntities {
    public static final BCRegistrar<BlockEntityType<?>> BLOCK_ENTITIES =
            BCRegistrar.create(Registries.BLOCK_ENTITY_TYPE, BCSilicon.MODID);

    public static final Supplier<BlockEntityType<TileLaser>> LASER =
            BLOCK_ENTITIES.register("laser",
                    () -> BlockEntityTypeUtilBC.create(TileLaser::new,
                            BCSiliconBlocks.LASER.get()));

    public static final Supplier<BlockEntityType<TileAssemblyTable>> ASSEMBLY_TABLE =
            BLOCK_ENTITIES.register("assembly_table",
                    () -> BlockEntityTypeUtilBC.create(TileAssemblyTable::new,
                            BCSiliconBlocks.ASSEMBLY_TABLE.get()));

    public static final Supplier<BlockEntityType<TileAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE =
            BLOCK_ENTITIES.register("advanced_crafting_table",
                    () -> BlockEntityTypeUtilBC.create(TileAdvancedCraftingTable::new,
                            BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get()));

    public static final Supplier<BlockEntityType<TileProgrammingTable>> PROGRAMMING_TABLE =
            BLOCK_ENTITIES.register("programming_table",
                    () -> BlockEntityTypeUtilBC.create(TileProgrammingTable::new,
                            BCSiliconBlocks.PROGRAMMING_TABLE.get()));

    // Shipped alongside the Programming Table since Ph7 registered the robot integration recipe.
    public static final Supplier<BlockEntityType<TileIntegrationTable>> INTEGRATION_TABLE =
            BLOCK_ENTITIES.register("integration_table",
                    () -> BlockEntityTypeUtilBC.create(TileIntegrationTable::new,
                            BCSiliconBlocks.INTEGRATION_TABLE.get()));

    public static void init() {
        BLOCK_ENTITIES.subscribe();
    }
}
