/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.container.ContainerFillerPlanner;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.marker.volume.EnumAddonSlot;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.lib.net.PacketBufferBC;

/**
 * Guards the Filler Planner's "no player inventory" invariant.
 * <p>
 * The Filler Planner is a pattern-planning screen with no item interaction — its pattern and
 * parameter "slots" are BuildCraft GUI elements, not vanilla {@link net.minecraft.world.inventory.Slot}s.
 * It used to call {@code addFullPlayerInventory(...)}, which left the player's 36 inventory slots
 * dangling in the empty space below the panel: items rendered as slot-less floating icons under a
 * stray "Inventory" label, because the GUI texture has no inventory cells. The fix dropped that
 * call, so the container must now expose ZERO slots. If player inventory is ever re-added, this fails.
 * <p>
 * The matching client-side changes (GUI height trimmed to the 81px panel, help ledger opted out via
 * {@code GuiFillerPlanner.shouldAddHelpLedger()}) are client-only and can't be instantiated
 * headlessly — they need in-client verification.
 */
public class FillerPlannerContainerTester {

    public static void testFillerPlannerHasNoSlots(GameTestHelper helper) {
        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        AddonFillerPlanner addon = new AddonFillerPlanner();
        ContainerFillerPlanner container = new ContainerFillerPlanner(1, mockPlayer.getInventory(), addon);

        if (!container.slots.isEmpty()) {
            throw new IllegalStateException(
                "Filler Planner is a pattern-only screen: its container must expose no slots "
                    + "(no player inventory). Found " + container.slots.size() + " slot(s) — "
                    + "did addFullPlayerInventory() get re-added?");
        }

        helper.succeed();
    }

    /**
     * A Filler standing on a box with a planner edits the planner's pattern and invert from its own screen. Its invert
     * button must show the planner's flag (it showed the Filler's own, which nothing writes while a planner is
     * attached, so a press changed nothing on screen), and either edit must save the box and reach its viewers
     * (neither marked the box data, so the planner's preview kept the old pattern).
     */
    public static void fillerScreenEditsReachThePlanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos boxPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos fillerRel = new BlockPos(2, 1, 3);
        boxes.addVolumeBox(boxPos);
        VolumeBox box = boxes.getVolumeBoxAt(boxPos);
        try {
            AddonFillerPlanner planner = new AddonFillerPlanner();
            planner.volumeBox = box;
            box.addons.put(EnumAddonSlot.EAST_UP_SOUTH, planner);
            planner.onAdded();

            // Facing south, the Filler takes its box from the cell behind it, to the north.
            helper.setBlock(fillerRel, BCBuildersBlocks.FILLER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            TileFiller filler = (TileFiller) level.getBlockEntity(helper.absolutePos(fillerRel));
            filler.onPlacedBy(null, ItemStack.EMPTY);
            helper.assertTrue(filler.addon == planner, "the Filler should take the planner of the box behind it");

            ContainerFiller menu = new ContainerFiller(1, helper.makeMockPlayer(GameType.CREATIVE).getInventory(),
                    filler);
            boxes.setDirty(false);
            menu.readMessage(ContainerFiller.NET_INVERT, new PacketBufferBC(Unpooled.buffer()), false, null);
            helper.assertTrue(planner.inverted, "the invert press should invert the planner");
            helper.assertTrue(menu.isInverted(), "the invert button should show the planner's flag");
            helper.assertTrue(boxes.isDirty(), "an invert from the Filler's screen should save and send the box");

            boxes.setDirty(false);
            PacketBufferBC pattern = new PacketBufferBC(Unpooled.buffer());
            planner.patternStatement.writeToBuffer(pattern);
            menu.readMessage(ContainerFiller.NET_STATEMENT, pattern, false, null);
            helper.assertTrue(boxes.isDirty(), "a pattern set from the Filler's screen should save and send the box");
            helper.succeed();
        } finally {
            helper.setBlock(fillerRel, Blocks.AIR);
            boxes.volumeBoxes.remove(box);
            boxes.markDirtyAndBroadcast();
        }
    }
}
