/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Anchors the pipe required-item fix ({@link SchematicBlockPipe}). BuildCraft pipes are all one
 * block (the pipe holder) with the type living in block-entity NBT, not the block state. The
 * generic {@link SchematicBlockDefault} resolves a placement's required item via
 * {@code blockState.getBlock().asItem()}, which returns the SAME item for every pipe — so a
 * blueprint of varied pipes used to list a single pipe type in the Builder's resource panel even
 * though each pipe still <em>placed</em> correctly (placement reads the captured BE NBT).
 *
 * <p>This pins, for three pipes spanning all three flow kinds (item / fluid / power), that each
 * captured pipe schematic (a) routes to {@link SchematicBlockPipe} and (b) resolves the correct,
 * <em>distinct</em> pipe item — directly guarding against the "all pipes collapse to one item"
 * regression.
 */
public class SchematicBlockPipeTester {

    /** Place a pipe of the given type, capture it as a schematic, and return its single required item. */
    private static ItemStack requiredItemFor(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        // Initialises the pipe (and its definition) on the BE exactly as a player placement would.
        tile.onPlacedBy(null, new ItemStack(pipeItem));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        SchematicBlockContext context = new SchematicBlockContext(
                level, BlockPos.ZERO, abs, state, state.getBlock());
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(context);
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        List<ItemStack> required = schematic.computeRequiredItems();
        helper.assertTrue(required.size() == 1,
                "A pipe should require exactly one item, got " + required.size() + " for "
                        + BuiltInRegistries.ITEM.getKey(pipeItem));
        return required.get(0);
    }

    /** Each pipe resolves to ITS OWN item, and distinct pipe types stay distinct. */
    public static void pipeSchematicResolvesPerTypeItem(GameTestHelper helper) {
        Item wood = BCTransportItems.PIPE_WOOD_ITEM.get();
        Item goldFluid = BCTransportItems.PIPE_GOLD_FLUID.get();
        Item diamondPower = BCTransportItems.PIPE_DIAMOND_POWER.get();

        ItemStack woodReq = requiredItemFor(helper, new BlockPos(1, 2, 1), wood);
        ItemStack goldReq = requiredItemFor(helper, new BlockPos(2, 2, 1), goldFluid);
        ItemStack diamondReq = requiredItemFor(helper, new BlockPos(3, 2, 1), diamondPower);

        helper.assertTrue(woodReq.is(wood),
                "Wooden item pipe must require the wooden item pipe, got "
                        + BuiltInRegistries.ITEM.getKey(woodReq.getItem()));
        helper.assertTrue(goldReq.is(goldFluid),
                "Gold fluid pipe must require the gold fluid pipe, got "
                        + BuiltInRegistries.ITEM.getKey(goldReq.getItem()));
        helper.assertTrue(diamondReq.is(diamondPower),
                "Diamond power pipe must require the diamond power pipe, got "
                        + BuiltInRegistries.ITEM.getKey(diamondReq.getItem()));

        // The direct regression guard: three different pipe types must not collapse to one item.
        helper.assertFalse(
                woodReq.getItem() == goldReq.getItem()
                        || woodReq.getItem() == diamondReq.getItem()
                        || goldReq.getItem() == diamondReq.getItem(),
                "Distinct pipe types must resolve to distinct required items (the reported 'all pipes are one' bug)");
        helper.succeed();
    }
}
