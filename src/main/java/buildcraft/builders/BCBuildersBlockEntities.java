/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.builders.tile.TileReplacer;

public class BCBuildersBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCBuilders.MODID);

    public static final Supplier<BlockEntityType<TileFiller>> FILLER = BLOCK_ENTITIES.register(
            "filler",
            () -> BlockEntityTypeUtilBC.create(TileFiller::new, BCBuildersBlocks.FILLER.get()));

    public static final Supplier<BlockEntityType<TileBuilder>> BUILDER = BLOCK_ENTITIES.register(
            "builder",
            () -> BlockEntityTypeUtilBC.create(TileBuilder::new, BCBuildersBlocks.BUILDER.get()));

    public static final Supplier<BlockEntityType<TileArchitectTable>> ARCHITECT = BLOCK_ENTITIES.register(
            "architect",
            () -> BlockEntityTypeUtilBC.create(TileArchitectTable::new, BCBuildersBlocks.ARCHITECT.get()));

    public static final Supplier<BlockEntityType<TileElectronicLibrary>> LIBRARY = BLOCK_ENTITIES.register(
            "library",
            () -> BlockEntityTypeUtilBC.create(TileElectronicLibrary::new, BCBuildersBlocks.LIBRARY.get()));

    public static final Supplier<BlockEntityType<TileReplacer>> REPLACER = BLOCK_ENTITIES.register(
            "replacer",
            () -> BlockEntityTypeUtilBC.create(TileReplacer::new, BCBuildersBlocks.REPLACER.get()));

    public static final Supplier<BlockEntityType<TileQuarry>> QUARRY = BLOCK_ENTITIES.register(
            "quarry",
            () -> BlockEntityTypeUtilBC.create(TileQuarry::new, BCBuildersBlocks.QUARRY.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
