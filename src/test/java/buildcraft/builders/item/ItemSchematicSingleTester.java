/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * A used single schematic clicked onto a block a placement replaces (short grass here) takes that block out of the
 * cell before it builds. A placement that then fails, for want of the items or because the block has no room (a
 * door's upper half blocked), must put the grass back: it used to leave the cell empty, so a click that placed
 * nothing still cost the world the grass. Seagrass too: taking it out leaves its water, which the schematic does not
 * build into, so the click places nothing and the seagrass must come back. A placement that goes through still
 * replaces the grass.
 */
public class ItemSchematicSingleTester {

    private static final BlockPos NO_ITEMS = new BlockPos(1, 2, 1);
    private static final BlockPos DOOR_BLOCKED = new BlockPos(3, 2, 1);
    private static final BlockPos PLACES = new BlockPos(1, 2, 4);
    private static final BlockPos SEAGRASS = new BlockPos(3, 2, 4);

    public static void testFailedPlacementKeepsTheClickedBlock(GameTestHelper helper) {
        BlockPos[] grassCells = {NO_ITEMS, DOOR_BLOCKED, PLACES, SEAGRASS};
        try {
            for (BlockPos grass : grassCells) {
                helper.setBlock(grass.below(), Blocks.DIRT);
                helper.setBlock(grass, grass == SEAGRASS ? Blocks.SEAGRASS : Blocks.SHORT_GRASS);
            }
            helper.setBlock(DOOR_BLOCKED.above(), Blocks.SKELETON_SKULL);
            List<String> broken = new ArrayList<>();

            click(helper, NO_ITEMS, schematicOf(helper, Blocks.STONE.defaultBlockState()), ItemStack.EMPTY);
            if (!helper.getBlockState(NO_ITEMS).is(Blocks.SHORT_GRASS)) {
                broken.add("a click with no stone to pay left " + helper.getBlockState(NO_ITEMS) + " for the grass");
            }

            click(helper, DOOR_BLOCKED, schematicOf(helper, Blocks.OAK_DOOR.defaultBlockState()),
                    new ItemStack(Items.OAK_DOOR));
            if (!helper.getBlockState(DOOR_BLOCKED).is(Blocks.SHORT_GRASS)) {
                broken.add("a door refused for the skull above it left " + helper.getBlockState(DOOR_BLOCKED)
                        + " for the grass");
            }
            if (!helper.getBlockState(DOOR_BLOCKED.above()).is(Blocks.SKELETON_SKULL)) {
                broken.add("the skull above the refused door became " + helper.getBlockState(DOOR_BLOCKED.above()));
            }

            click(helper, SEAGRASS, schematicOf(helper, Blocks.STONE.defaultBlockState()), new ItemStack(Items.STONE));
            if (!helper.getBlockState(SEAGRASS).is(Blocks.SEAGRASS)) {
                broken.add("a click that built nothing into the seagrass's water left " + helper.getBlockState(SEAGRASS)
                        + " for the seagrass");
            }

            Player payer = click(helper, PLACES, schematicOf(helper, Blocks.STONE.defaultBlockState()),
                    new ItemStack(Items.STONE));
            if (!helper.getBlockState(PLACES).is(Blocks.STONE)) {
                broken.add("a paid click must replace the grass with stone, found " + helper.getBlockState(PLACES));
            }
            if (payer.getInventory().countItem(Items.STONE) != 0) {
                broken.add("the placed stone was not taken from the player");
            }
            helper.assertTrue(broken.isEmpty(), broken.size() + " broken: " + String.join(" | ", broken));
            helper.succeed();
        } finally {
            for (BlockPos grass : grassCells) {
                helper.setBlock(grass, Blocks.AIR);
                helper.setBlock(grass.above(), Blocks.AIR);
                helper.setBlock(grass.below(), Blocks.AIR);
            }
        }
    }

    /** A used single schematic of {@code state}, as capturing it would make. */
    private static ItemStack schematicOf(GameTestHelper helper, BlockState state) {
        BlockPos sample = helper.absolutePos(new BlockPos(5, 2, 6));
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(helper.getLevel(), sample, sample, state, state.getBlock()));
        ItemStack stack = new ItemStack(BCBuildersItems.SCHEMATIC_SINGLE_USED.get());
        CompoundTag itemData = new CompoundTag();
        itemData.put(ItemSchematicSingle.NBT_KEY, SchematicBlockManager.writeToNBT(schematic));
        NBTUtilBC.setItemData(stack, itemData);
        return stack;
    }

    /** A survival player holding {@code schematic}, with {@code pay} in their inventory, clicks the top of the grass
     *  at {@code relPos}. */
    private static Player click(GameTestHelper helper, BlockPos relPos, ItemStack schematic, ItemStack pay) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
        if (!pay.isEmpty()) {
            player.getInventory().add(pay);
        }
        BlockPos abs = helper.absolutePos(relPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BCBuildersItems.SCHEMATIC_SINGLE_USED.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return player;
    }
}
