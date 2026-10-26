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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMarkerWrench;
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
 * Markers that still back a box being lifted are nobody's to take, and a wrench leaves a box another player drags.
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

    /**
     * A resize in progress (a connected volume lifted into a floating box, the drag still open) still owns its
     * markers: a Filler, an Architect Table or a Quarry placed against them, or against the lifted area, finds no area
     * and takes nothing. A machine that took the markers would break them under the lifted box and, for the Filler and
     * the Architect Table, leave a second box next to it.
     */
    public static void machinesLeaveMarkersBackingALiftedBox(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos marker1 = new BlockPos(1, 2, 1);
        BlockPos marker2 = new BlockPos(4, 2, 1);
        BlockPos marker3 = new BlockPos(1, 2, 4);
        BlockPos corner = new BlockPos(4, 2, 4);
        // Each machine's back (where it looks for an area) is a marker, except the second Quarry's, which is the
        // empty cell (1,2,2) inside the lifted area, so it takes the area through the markers around it.
        BlockPos[] machineRels = {new BlockPos(0, 2, 1), new BlockPos(1, 2, 0), new BlockPos(2, 2, 1), new BlockPos(0, 2, 2)};
        Direction[] machineFacings = {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.WEST};
        String[] names = {"Filler", "Architect Table", "Quarry at a marker", "Quarry beside the area"};
        BlockPos lateFillerRel = new BlockPos(0, 2, 4);
        arena.guard(() -> {
            arena.place(marker1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker3, BCCoreBlocks.MARKER_VOLUME.get());

            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, marker1)
                && arena.isRegistered(VolumeCache.INSTANCE, marker2) && arena.isRegistered(VolumeCache.INSTANCE, marker3), () -> {
                ServerLevel level = helper.getLevel();
                LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
                BlockPos[] markers = {arena.abs(marker1), arena.abs(marker2), arena.abs(marker3)};
                BlockPos[] machines = new BlockPos[machineRels.length];
                for (int i = 0; i < machines.length; i++) {
                    machines[i] = arena.abs(machineRels[i]);
                }
                Set<Integer> itemsBefore = new HashSet<>();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                    itemsBefore.add(item.getId());
                }
                try {
                    if (!(level.getBlockEntity(markers[0]) instanceof TileMarkerVolume volume)) {
                        throw new IllegalStateException("no volume marker tile at " + marker1);
                    }
                    volume.onManualConnectionAttempt(null);
                    if (!volume.max().equals(arena.abs(corner))) {
                        throw new IllegalStateException("Precondition: the markers did not connect into a box");
                    }

                    // Lift the area at marker2 and keep the drag open.
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    Vec3 stand = Vec3.atBottomCenterOf(arena.abs(new BlockPos(2, 2, 6)));
                    player.setPos(stand.x, stand.y, stand.z);
                    player.setYRot(180);
                    player.setXRot(0);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCoreItems.MARKER_WRENCH.get()));
                    if (!ItemMarkerWrench.onUseOnVolumeMarker(level, player, markers[1], Vec3.atCenterOf(markers[1]))) {
                        throw new IllegalStateException("Precondition: the corner marker could not be lifted");
                    }
                    VolumeBox lifted = boxes.volumeBoxes.stream().filter(box -> box.isEditingBy(player)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Precondition: no box is being dragged"));
                    if (!lifted.hasSourceMarkers()) {
                        throw new IllegalStateException("Precondition: the lifted box is not backed by the markers");
                    }

                    for (int i = 0; i < machines.length; i++) {
                        BlockState state = (i == 0 ? BCBuildersBlocks.FILLER : i == 1 ? BCBuildersBlocks.ARCHITECT
                            : BCBuildersBlocks.QUARRY).get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, machineFacings[i]);
                        helper.setBlock(machineRels[i], state);
                        BlockEntity tile = level.getBlockEntity(machines[i]);
                        if (tile instanceof TileFiller filler) {
                            filler.onPlacedBy(null, ItemStack.EMPTY);
                            if (filler.hasBox()) {
                                throw new IllegalStateException(names[i] + " took an area from the lifted markers");
                            }
                        } else if (tile instanceof TileArchitectTable table) {
                            table.onPlacedBy(null, ItemStack.EMPTY);
                            if (table.getIsValid()) {
                                throw new IllegalStateException(names[i] + " took an area from the lifted markers");
                            }
                        } else if (tile instanceof TileQuarry quarry) {
                            quarry.onPlacedBy(null, ItemStack.EMPTY);
                        } else {
                            throw new IllegalStateException("no machine tile for the " + names[i]);
                        }
                        for (BlockPos marker : markers) {
                            if (level.getBlockState(marker).getBlock() != BCCoreBlocks.MARKER_VOLUME.get()) {
                                throw new IllegalStateException("The " + names[i] + " broke the marker at " + arena.rel(marker)
                                    + " that backs the lifted box");
                            }
                        }
                        long over = boxes.volumeBoxes.stream().filter(box -> box.box.contains(markers[0])).count();
                        if (over != 1 || !boxes.volumeBoxes.contains(lifted) || !lifted.isEditing() || !lifted.hasSourceMarkers()) {
                            throw new IllegalStateException("After the " + names[i] + " the lifted area should still be covered "
                                + "by the lifted box alone, found " + over + " box(es)");
                        }
                        helper.setBlock(machineRels[i], Blocks.AIR);
                    }

                    // A marker that joined the connection after the lift is not on the lifted box's list, yet taking it
                    // takes the whole connection: here marker3 stands in for one, left off the list of a box lifted
                    // from the other two and dragged short of it (so no box covers it either).
                    boxes.volumeBoxes.remove(lifted);
                    boxes.volumeBoxes.add(VolumeBox.fromMarkers(level, lifted.box.min(),
                        lifted.box.max().north(), List.of(markers[0], markers[1])));
                    if (boxes.getVolumeBoxAt(markers[2]) != null) {
                        throw new IllegalStateException("Precondition: a box covers the marker that stands in for a late one");
                    }
                    helper.setBlock(lateFillerRel, BCBuildersBlocks.FILLER.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                    if (!(level.getBlockEntity(arena.abs(lateFillerRel)) instanceof TileFiller lateFiller)) {
                        throw new IllegalStateException("no Filler tile at " + lateFillerRel);
                    }
                    lateFiller.onPlacedBy(null, ItemStack.EMPTY);
                    if (lateFiller.hasBox() || level.getBlockState(markers[2]).getBlock() != BCCoreBlocks.MARKER_VOLUME.get()) {
                        throw new IllegalStateException("A Filler took the lifted connection through a marker that joined it "
                            + "after the lift");
                    }

                    // Markers that stand inside a confirmed floating box are not a machine's to take either: the box
                    // already covers the area, and taking the markers would put a second box over it.
                    boxes.volumeBoxes.removeIf(box -> box.box.contains(markers[0]));
                    boxes.volumeBoxes.add(VolumeBox.fromMarkers(level, lifted.box.min(), lifted.box.max(), List.of()));
                    helper.setBlock(machineRels[0], BCBuildersBlocks.FILLER.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, machineFacings[0]));
                    if (!(level.getBlockEntity(machines[0]) instanceof TileFiller coveredFiller)) {
                        throw new IllegalStateException("no Filler tile at " + machineRels[0]);
                    }
                    coveredFiller.onPlacedBy(null, ItemStack.EMPTY);
                    long covering = boxes.volumeBoxes.stream().filter(box -> box.box.contains(markers[0])).count();
                    if (coveredFiller.hasBox() || covering != 1 || boxes.getOwnedBy(machines[0]) != null
                        || boxes.getOneShotAreaAt(markers[0]) == null
                        || level.getBlockState(markers[0]).getBlock() != BCCoreBlocks.MARKER_VOLUME.get()) {
                        throw new IllegalStateException("A Filler took the markers standing inside a floating box (" + covering
                            + " box(es) over them now)");
                    }
                } finally {
                    // Never leave a box or a machine behind, whatever failed.
                    for (BlockPos machine : machines) {
                        level.setBlock(machine, Blocks.AIR.defaultBlockState(), 3);
                    }
                    level.setBlock(arena.abs(lateFillerRel), Blocks.AIR.defaultBlockState(), 3);
                    if (boxes.volumeBoxes.removeIf(box -> box.box.contains(markers[0]) || box.box.contains(markers[1])
                            || box.box.contains(markers[2]))) {
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

    /**
     * A lone volume marker has no connection, so a Filler placed against it takes nothing but its one cell: the
     * Filler (or Architect Table) works on that cell and owns no box, and the marker stays where it is and can still be
     * linked or lifted.
     */
    public static void aLoneMarkerGivesTheFillerNoOwnedBox(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos markerRel = new BlockPos(2, 2, 2);
        // Facing north, so the Filler's back is the marker at (2,2,2).
        BlockPos fillerRel = new BlockPos(2, 2, 1);
        // Facing south, so the Architect Table's back is the same marker, from the other side.
        BlockPos tableRel = new BlockPos(2, 2, 3);
        arena.guard(() -> {
            arena.place(markerRel, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, markerRel), () -> {
                ServerLevel level = helper.getLevel();
                LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
                BlockPos marker = arena.abs(markerRel);
                BlockPos fillerAbs = arena.abs(fillerRel);
                BlockPos tableAbs = arena.abs(tableRel);
                try {
                    helper.setBlock(fillerRel, BCBuildersBlocks.FILLER.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    if (!(level.getBlockEntity(fillerAbs) instanceof TileFiller filler)) {
                        throw new IllegalStateException("no Filler tile at " + fillerRel);
                    }
                    filler.onPlacedBy(null, ItemStack.EMPTY);
                    if (!filler.hasBox() || !filler.getBox().min().equals(marker) || !filler.getBox().max().equals(marker)) {
                        throw new IllegalStateException("The Filler should work on the marker's one cell");
                    }
                    if (boxes.getOwnedBy(fillerAbs) != null || boxes.volumeBoxes.stream().anyMatch(box -> box.box.contains(marker))) {
                        throw new IllegalStateException("The Filler took no markers but a volume box covers the lone marker");
                    }
                    if (level.getBlockState(marker).getBlock() != BCCoreBlocks.MARKER_VOLUME.get()) {
                        throw new IllegalStateException("The lone marker is gone");
                    }

                    // The Architect Table reads the same one cell and owns no box either.
                    helper.setBlock(tableRel, BCBuildersBlocks.ARCHITECT.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    if (!(level.getBlockEntity(tableAbs) instanceof TileArchitectTable table)) {
                        throw new IllegalStateException("no Architect Table tile at " + tableRel);
                    }
                    table.onPlacedBy(null, ItemStack.EMPTY);
                    if (!table.getIsValid()) {
                        throw new IllegalStateException("The Architect Table should scan the marker's one cell");
                    }
                    if (boxes.getOwnedBy(tableAbs) != null || boxes.volumeBoxes.stream().anyMatch(box -> box.box.contains(marker))) {
                        throw new IllegalStateException("The Architect Table took no markers but a volume box covers the "
                            + "lone marker");
                    }
                } finally {
                    level.setBlock(tableAbs, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(fillerAbs, Blocks.AIR.defaultBlockState(), 3);
                    if (boxes.volumeBoxes.removeIf(box -> box.box.contains(marker) || box.isOwnedBy(fillerAbs))) {
                        boxes.markDirtyAndBroadcast();
                    }
                }
                arena.finish();
            }, "The volume marker never registered in the cache");
        });
    }

    /**
     * A box one player is dragging stays with that player: a machine placed against it adopts nothing (it would copy
     * a size that is still moving and never follow it), and another player's sneak-click on it does not remove it
     * (that would orphan the drag). Once nobody drags it, the box is offered to a machine and the click removes it.
     */
    public static void sneakUseLeavesABoxAnotherPlayerDrags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos max = helper.absolutePos(new BlockPos(4, 2, 4));
        VolumeBox free = new VolumeBox(level, min);
        free.box.extendToEncompass(max);
        boxes.volumeBoxes.add(free);
        try {
            Player dragger = helper.makeMockPlayer(GameType.SURVIVAL);
            free.setPlayer(dragger);
            free.setHeldDistOldMinOldMax(max, 2, min, max);
            if (boxes.getFreeVolumeBoxAt(min) != null) {
                throw new IllegalStateException("A box being dragged is offered to a machine to adopt");
            }

            // Another player sneak-uses a Volume Wrench in the air, aimed west along the empty z=2 row into the box's
            // east face. Air clicks only act when no block is in reach, so check that first.
            Player other = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos eyeBlock = helper.absolutePos(new BlockPos(5, 2, 2));
            other.setPos(eyeBlock.getX() + 0.8, eyeBlock.getY() + 0.5 - other.getEyeHeight(), eyeBlock.getZ() + 0.5);
            other.setXRot(0);
            other.setYRot(90);
            other.setShiftKeyDown(true);
            Vec3 eye = other.getEyePosition();
            Vec3 reach = eye.add(other.getLookAngle().scale(other.blockInteractionRange()));
            if (level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, other))
                    .getType() != HitResult.Type.MISS) {
                throw new IllegalStateException("Precondition: a block is in reach, so the air click would do nothing");
            }
            if (free.box.getBoundingBox().clip(eye, reach).isEmpty()) {
                throw new IllegalStateException("Precondition: the click does not aim at the box");
            }
            ItemStack wrench = new ItemStack(BCCoreItems.VOLUME_WRENCH.get());
            other.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            wrench.getItem().use(level, other, InteractionHand.MAIN_HAND);
            if (!boxes.volumeBoxes.contains(free) || !free.isEditingBy(dragger)) {
                throw new IllegalStateException("A sneak-click by another player took the box from its dragger");
            }

            // Control: once nobody drags it, a machine may adopt it and the same click removes it, so the checks above
            // reached the adoption and the removal.
            free.cancelEditing();
            if (boxes.getFreeVolumeBoxAt(min) != free) {
                throw new IllegalStateException("Control: an undragged box is not offered to a machine either");
            }
            wrench.getItem().use(level, other, InteractionHand.MAIN_HAND);
            if (boxes.volumeBoxes.contains(free)) {
                throw new IllegalStateException("Control: the sneak-click did not remove an undragged box either");
            }
            helper.succeed();
        } finally {
            if (boxes.volumeBoxes.remove(free)) {
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
