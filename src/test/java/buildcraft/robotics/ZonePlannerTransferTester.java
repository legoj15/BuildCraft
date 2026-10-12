/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

/**
 * Game test for the Zone Planner's slot transfer ({@link TileZonePlanner#serverTick}). Exercises the full
 * round trip a player drives through the GUI slots: an INPUT read (a ZONE {@link ItemMapLocation} + a coloured
 * paintbrush store the zone into that colour's tile layer) and an OUTPUT write (the layer is stamped back onto
 * a fresh map). Pins the world&lt;-&gt;tile-relative offset (layers are stored relative to the tile, the item
 * carries absolute coords) and {@link ItemMapLocation#setZone}/{@code getZone}. Also pins the output refusal:
 * a brush whose colour layer is empty must not run the transfer (no empty-zone maps, no map consumed), and
 * an inventory edit must mark the chunk dirty (the setChanged callback wiring that keeps rack contents from
 * silently vanishing on relog). A game test because it builds real {@link ItemStack}s with data components;
 * the {@code gameTestServer} run sets {@code buildcraft.dev=true}, so the otherwise dev-gated
 * {@code MAP_LOCATION} item exists here.
 */
public class ZonePlannerTransferTester {

    public static void transferRoundTrip(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCRoboticsBlocks.ZONE_PLANNER.get());

        //? if >=1.21.10 {
        TileZonePlanner tile = helper.getBlockEntity(pos, TileZonePlanner.class);
        //?} else {
        /*TileZonePlanner tile = helper.getBlockEntity(pos);*/
        //?}

        // A marked block at an absolute world coord (offset from this tile's position).
        BlockPos tilePos = tile.getBlockPos();
        int ax = tilePos.getX() + 5;
        int az = tilePos.getZ() + 7;

        ZonePlan source = new ZonePlan();
        source.set(ax, az, true);
        ItemStack inputMap = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ItemMapLocation.setZone(inputMap, source);

        // --- INPUT: a RED paintbrush + the ZONE map store the zone into the RED layer ---
        tile.invInputPaintbrush.setStackInSlot(0,
                ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), DyeColor.RED));
        tile.invInputMapLocation.setStackInSlot(0, inputMap);
        drive(tile);

        ZonePlan redLayer = tile.layers[DyeColor.RED.ordinal()];
        helper.assertTrue(redLayer.get(5, 7),
                "input stores the zone tile-relative ((5,7) = absolute minus this tile's pos) in the RED layer");
        helper.assertTrue(tile.invInputMapLocation.getStackInSlot(0).isEmpty(),
                "the input map location is consumed");
        helper.assertTrue(tile.invInputResult.getStackInSlot(0).getItem() instanceof ItemMapLocation,
                "a blank map location is returned to the input result slot");

        // --- OUTPUT: a RED paintbrush + a fresh map write the RED layer back out ---
        tile.invOutputPaintbrush.setStackInSlot(0,
                ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), DyeColor.RED));
        tile.invOutputMapLocation.setStackInSlot(0, new ItemStack(BCCoreItems.MAP_LOCATION.get()));
        drive(tile);

        ItemStack result = tile.invOutputResult.getStackInSlot(0);
        helper.assertTrue(result.getItem() instanceof ItemMapLocation, "output writes a map location into the result slot");
        ZonePlan roundTripped = result.getItem() instanceof ItemMapLocation iml
                && iml.getZone(result) instanceof ZonePlan zp ? zp : null;
        helper.assertTrue(roundTripped != null && roundTripped.get(ax, az),
                "the zone round-trips back to its original absolute world coords");
        helper.assertFalse(roundTripped != null && roundTripped.get(ax + 1, az), "and nothing else leaks in");

        // --- REFUSAL: a BLACK paintbrush (its layer was never painted) + a fresh map must not run ---
        // The gate must fire before any progress: a short spin pins the counter at idle (pre-fix it climbs
        // 0,1,2...), then a full drive window proves no output item appears and the map is never consumed.
        tile.invOutputResult.setStackInSlot(0, ItemStack.EMPTY);
        tile.invOutputPaintbrush.setStackInSlot(0,
                ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), DyeColor.BLACK));
        tile.invOutputMapLocation.setStackInSlot(0, new ItemStack(BCCoreItems.MAP_LOCATION.get()));
        for (int i = 0; i < 5; i++) {
            tile.serverTick();
        }
        helper.assertTrue(tile.getProgressOutput() == -1,
                "the output transfer never starts progressing for an unpainted colour");
        drive(tile);

        helper.assertTrue(tile.invOutputResult.getStackInSlot(0).isEmpty(),
                "an unpainted colour writes no map at all, not an empty-zone map");
        helper.assertTrue(tile.invOutputMapLocation.getStackInSlot(0).getCount() == 1,
                "the would-be-written map location is left untouched");

        // --- PERSISTENCE: an inventory edit must mark the chunk dirty via the tile's setChanged ---
        // Vanilla re-caches a block entity's saved NBT only when setChanged() runs, so a rack edit with
        // no wired callback would persist the stale pre-edit state — a stashed brush vanished on relog.
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());
        //? if >=1.21.10 {
        chunk.tryMarkSaved();
        //?} else {
        /*chunk.setUnsaved(false);*/
        //?}
        helper.assertFalse(chunk.isUnsaved(), "baseline: the chunk flag is clean before the edit");
        tile.invPaintbrushes.setStackInSlot(0,
                ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), DyeColor.BLACK));
        helper.assertTrue(chunk.isUnsaved(), "a rack edit marks the chunk dirty so the brush survives the save");

        helper.succeed();
    }

    /** Drives serverTick past the 200-tick progress gate to force one transfer to completion. */
    private static void drive(TileZonePlanner tile) {
        for (int i = 0; i < 205; i++) {
            tile.serverTick();
        }
    }
}
