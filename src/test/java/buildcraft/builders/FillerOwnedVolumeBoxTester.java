/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.tile.TileFiller;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;

/**
 * A Filler placed against connected survival volume markers, or against a floating box lifted off them, turns that
 * area into a volume box it owns: the box covers the area, no wrench can remove it, and it goes when the Filler does.
 */
public class FillerOwnedVolumeBoxTester {

    /** Generous wait for the markers to join the cache (they do on the first level tick); counted from the cell
     *  ticking, so the manifest's max_ticks outlasts {@code EntityArenaUtil.CELL_READY_TICKS + REGISTER_TICKS}. */
    private static final int REGISTER_TICKS = 40;

    /**
     * The floating box a Marker Wrench leaves when a resize of connected markers is confirmed behaves like the markers
     * it replaced: a Filler placed against it ends with exactly one box over that area, the Filler's own, and the box
     * goes with the Filler. A machine that merely consumed the floating box (as a Quarry does) would own nothing; one
     * that left it standing next to a new owned box would leave two.
     */
    public static void floatingBoxBecomesTheFillersOwnedBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos fillerRel = new BlockPos(0, 2, 1);
        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos max = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos fillerAbs = helper.absolutePos(fillerRel);
        try {
            // The state a confirmed resize leaves: one-shot, its markers broken, nobody dragging it.
            VolumeBox floating = VolumeBox.fromMarkers(level, min, max, List.of(min, max));
            floating.breakSourceMarkers();
            boxes.volumeBoxes.add(floating);
            if (boxes.getOneShotAreaAt(min) == null || boxes.getFreeVolumeBoxAt(min) != null) {
                throw new IllegalStateException("Precondition: the floating box is not offered as an area to take");
            }

            // Facing west, so the Filler's back (where it looks for an area) is the floating box at (1,2,1).
            BlockState state = BCBuildersBlocks.FILLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
            helper.setBlock(fillerRel, state);
            if (!(level.getBlockEntity(fillerAbs) instanceof TileFiller filler)) {
                throw new IllegalStateException("no Filler tile at " + fillerRel);
            }
            filler.onPlacedBy(null, ItemStack.EMPTY);

            VolumeBox owned = boxes.getOwnedBy(fillerAbs);
            if (owned == null) {
                throw new IllegalStateException("The Filler took the floating box but owns no volume box");
            }
            if (!owned.box.min().equals(min) || !owned.box.max().equals(max)) {
                throw new IllegalStateException("The owned box is " + owned.box.min() + " to " + owned.box.max()
                    + ", not the floating box's " + min + " to " + max);
            }
            if (!filler.getBox().min().equals(min) || !filler.getBox().max().equals(max)) {
                throw new IllegalStateException("The Filler's working area is not the floating box's");
            }
            long over = boxes.volumeBoxes.stream().filter(box -> box.box.contains(min)).count();
            if (over != 1 || boxes.volumeBoxes.contains(floating) || owned.isOneShot()) {
                throw new IllegalStateException("Expected the one box the Filler owns over the area, found " + over
                    + (boxes.volumeBoxes.contains(floating) ? " (the floating box is still there)" : ""));
            }

            helper.setBlock(fillerRel, Blocks.AIR);
            if (boxes.getOwnedBy(fillerAbs) != null || boxes.volumeBoxes.stream().anyMatch(box -> box.box.contains(min))) {
                throw new IllegalStateException("The Filler is gone but a box is still over its area");
            }
            helper.succeed();
        } finally {
            // Never leave a box in the dimension's saved data, whatever failed.
            if (boxes.volumeBoxes.removeIf(box -> box.box.contains(min) || box.isOwnedBy(fillerAbs))) {
                boxes.markDirtyAndBroadcast();
            }
        }
    }

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

                    // Sneak-use a Volume Wrench in the air, aimed west along the empty z=2 row into the box's east
                    // face: the removal path. Air clicks only act when no block is in reach, so check that first.
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    BlockPos eyeBlock = arena.abs(new BlockPos(5, 2, 2));
                    player.setPos(eyeBlock.getX() + 0.8, eyeBlock.getY() + 0.5 - player.getEyeHeight(), eyeBlock.getZ() + 0.5);
                    player.setXRot(0);
                    player.setYRot(90);
                    player.setShiftKeyDown(true);
                    Vec3 eye = player.getEyePosition();
                    Vec3 reach = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
                    if (level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
                            .getType() != HitResult.Type.MISS) {
                        throw new IllegalStateException("Precondition: a block is in reach, so the air click would do nothing");
                    }
                    if (owned.box.getBoundingBox().clip(eye, reach).isEmpty()) {
                        throw new IllegalStateException("Precondition: the click does not aim at the box");
                    }
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

                    // Control: the same click on a free box does remove it, so the check above reached removal.
                    boxes.addVolumeBox(arena.abs(new BlockPos(4, 2, 2)));
                    VolumeBox free = boxes.getVolumeBoxAt(arena.abs(new BlockPos(4, 2, 2)));
                    wrench.getItem().use(level, player, InteractionHand.MAIN_HAND);
                    if (boxes.volumeBoxes.contains(free)) {
                        boxes.volumeBoxes.remove(free);
                        boxes.markDirtyAndBroadcast();
                        throw new IllegalStateException("Control: the sneak-click did not remove a free box either");
                    }
                } finally {
                    // Never leave a box in the dimension's saved data, whatever failed.
                    VolumeBox leftover;
                    while ((leftover = boxes.getOwnedBy(fillerAbs)) != null) {
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
