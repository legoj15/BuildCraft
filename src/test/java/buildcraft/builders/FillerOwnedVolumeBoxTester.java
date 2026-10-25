/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import buildcraft.builders.tile.TileFiller;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.volume.Addon;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;

/**
 * A Filler placed against connected survival volume markers turns them into a volume box it owns: the box covers the
 * markers' area, no wrench can remove it, and it goes when the Filler does.
 */
public class FillerOwnedVolumeBoxTester {

    /** Generous wait for the markers to join the cache (they do on the first level tick); counted from the cell
     *  ticking, so the manifest's max_ticks outlasts {@code EntityArenaUtil.CELL_READY_TICKS + REGISTER_TICKS}. */
    private static final int REGISTER_TICKS = 40;

    public static void survivalMarkersBecomeABoxTheFillerOwns(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos marker1 = new BlockPos(1, 2, 1);
        BlockPos marker2 = new BlockPos(4, 2, 1);
        BlockPos marker3 = new BlockPos(1, 2, 4);
        BlockPos corner = new BlockPos(4, 2, 4);
        // Facing west, so the Filler's back (where it looks for an area) is the marker at (1,2,1).
        BlockPos fillerRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(marker1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker3, BCCoreBlocks.MARKER_VOLUME.get());

            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, marker1)
                && arena.isRegistered(VolumeCache.INSTANCE, marker2) && arena.isRegistered(VolumeCache.INSTANCE, marker3), () -> {
                ServerLevel level = helper.getLevel();
                LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
                BlockPos fillerAbs = arena.abs(fillerRel);
                Set<Integer> itemsBefore = new HashSet<>();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                    itemsBefore.add(item.getId());
                }
                try {
                    if (!(level.getBlockEntity(arena.abs(marker1)) instanceof TileMarkerVolume volume)) {
                        throw new IllegalStateException("no volume marker tile at " + marker1);
                    }
                    volume.onManualConnectionAttempt(null);
                    if (!volume.max().equals(arena.abs(corner))) {
                        throw new IllegalStateException("Precondition: the markers did not connect into a box");
                    }

                    arena.place(fillerRel, BCBuildersBlocks.FILLER.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                    if (!(level.getBlockEntity(fillerAbs) instanceof TileFiller filler)) {
                        throw new IllegalStateException("no Filler tile at " + fillerRel);
                    }
                    filler.onPlacedBy(null, ItemStack.EMPTY);

                    VolumeBox owned = boxes.getOwnedBy(fillerAbs);
                    if (owned == null) {
                        throw new IllegalStateException("The Filler took the markers but owns no volume box");
                    }
                    if (!owned.box.min().equals(arena.abs(marker1)) || !owned.box.max().equals(arena.abs(corner))) {
                        throw new IllegalStateException("The owned box is " + arena.rel(owned.box.min()) + " to "
                            + arena.rel(owned.box.max()) + ", not the markers' " + marker1 + " to " + corner);
                    }
                    if (!filler.getBox().min().equals(owned.box.min()) || !filler.getBox().max().equals(owned.box.max())) {
                        throw new IllegalStateException("The Filler's working area is not its box");
                    }

                    // Sneak-use a Volume Wrench, aimed straight down at the box from above: the removal path.
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    BlockPos centre = arena.abs(new BlockPos(2, 2, 2));
                    player.setPos(centre.getX() + 0.5, centre.getY() + 3 - player.getEyeHeight(), centre.getZ() + 0.5);
                    player.setXRot(90);
                    player.setYRot(0);
                    player.setShiftKeyDown(true);
                    ItemStack wrench = new ItemStack(BCCoreItems.VOLUME_WRENCH.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
                    wrench.getItem().use(level, player, InteractionHand.MAIN_HAND);
                    if (boxes.getOwnedBy(fillerAbs) == null) {
                        throw new IllegalStateException("A sneak-click with the Volume Wrench removed the Filler's box");
                    }

                    arena.remove(fillerRel);
                    if (boxes.getOwnedBy(fillerAbs) != null || boxes.volumeBoxes.contains(owned)) {
                        throw new IllegalStateException("The Filler is gone but its box is still there");
                    }
                } finally {
                    // Never leave a box in the dimension's saved data, whatever failed.
                    VolumeBox leftover;
                    while ((leftover = boxes.getOwnedBy(fillerAbs)) != null) {
                        leftover.addons.values().forEach(Addon::onRemoved);
                        boxes.volumeBoxes.remove(leftover);
                        boxes.markDirtyAndBroadcast();
                    }
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                        if (!itemsBefore.contains(item.getId())) {
                            item.discard();
                        }
                    }
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }
}
