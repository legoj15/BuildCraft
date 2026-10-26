/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.Date;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;

/** {@link TileReplacer#doReplace} with an empty "to" slot removes the matches: they become air in the new blueprint. */
public class TileReplacerRemoveTester {

    private static void expect(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static ISchematicBlock schematic(ServerLevel level, BlockPos at, BlockState state) {
        return SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, at, at, state, state.getBlock()));
    }

    public static void emptyToSlotRemovesMatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCBuildersBlocks.REPLACER.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(pos);
        expect(level.getBlockEntity(abs) instanceof TileReplacer, "the Replacer's tile must exist once placed");
        TileReplacer tile = (TileReplacer) level.getBlockEntity(abs);

        ISchematicBlock stone = schematic(level, abs, Blocks.STONE.defaultBlockState());
        ISchematicBlock planks = schematic(level, abs, Blocks.OAK_PLANKS.defaultBlockState());
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(3, 1, 1);
        blueprint.offset = BlockPos.ZERO;
        blueprint.facing = Direction.NORTH;
        blueprint.palette.add(stone);
        blueprint.palette.add(planks);
        blueprint.data = new int[] { 0, 1, 0 };
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        ItemStack from = new ItemStack(BCBuildersItems.SCHEMATIC_SINGLE_USED.get());
        CompoundTag itemData = new CompoundTag();
        itemData.put(ItemSchematicSingle.NBT_KEY, SchematicBlockManager.writeToNBT(stone));
        NBTUtilBC.setItemData(from, itemData);

        tile.invSnapshot.setStackInSlot(0, BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "remove-test")));
        tile.invSchematicFrom.setStackInSlot(0, from);
        try {
            tile.doReplace(null);

            Snapshot.Header header = ItemSnapshot.getHeader(tile.invSnapshot.getStackInSlot(0));
            expect(header != null && !header.key.equals(blueprint.key),
                    "an empty \"to\" slot must still produce a new blueprint");
            Snapshot result = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
            expect(result instanceof Blueprint, "the new blueprint must be stored");
            Blueprint removed = (Blueprint) result;
            expect(removed.countNonAirCells() == 1,
                    "both stone cells must become air, leaving only the planks; non-air cells: " + removed.countNonAirCells());
            BlockState kept = removed.palette.get(removed.data[1]).getBlockStateForRender();
            expect(kept != null && kept.is(Blocks.OAK_PLANKS), "the unmatched planks cell must be untouched, got " + kept);
            expect(!tile.invSchematicFrom.getStackInSlot(0).isEmpty(), "the \"from\" schematic must not be consumed");
            helper.succeed();
        } finally {
            tile.invSnapshot.setStackInSlot(0, ItemStack.EMPTY);
            tile.invSchematicFrom.setStackInSlot(0, ItemStack.EMPTY);
            helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
