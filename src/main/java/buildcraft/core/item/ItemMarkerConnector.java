/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.item;

import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.PositionUtil.Line;
import buildcraft.lib.misc.PositionUtil.LineSkewResult;
import buildcraft.lib.misc.VecUtil;

import buildcraft.core.marker.PathSubCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.tile.TileMarkerVolume;

public class ItemMarkerConnector extends Item {

    private static final Identifier ADVANCEMENT_VOLUME_MARKER = Identifier.parse("buildcraftunofficial:markers");
    private static final Identifier ADVANCEMENT_PATH_MARKER = Identifier.parse("buildcraftunofficial:path_markers");

    public ItemMarkerConnector(Item.Properties properties) {
        super(properties);
    }

    @Override
    //? if >=1.21.10 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useImpl(level, player, hand);
    }
    //?} else {
    /*// 1.21.1: Item.use returns InteractionResultHolder<ItemStack>. Wrap the shared InteractionResult
    // logic (useImpl) with the held stack — InteractionResult.SUCCESS is version-neutral.
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(useImpl(level, player, hand), player.getItemInHand(hand));
    }*/
    //?}

    private InteractionResult useImpl(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                if (interactCache(cache.getSubCache(level), player)) {
                    return InteractionResult.SUCCESS;
                }
            }
        }
        InteractionResult volumeResult = onUseVolumeBoxes(level, player);
        if (volumeResult.consumesAction()) {
            return volumeResult;
        }
        // Always consume the action so the player gets swing feedback
        return InteractionResult.SUCCESS;
    }

    private static <S extends MarkerSubCache<?>> boolean interactCache(S cache, Player player) {
        MarkerLineInteraction best = null;
        Vec3 playerPos = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 playerLook = player.getLookAngle();
        for (BlockPos marker : cache.getAllMarkers()) {
            ImmutableList<BlockPos> possibles = cache.getValidConnections(marker);
            for (BlockPos possible : possibles) {
                MarkerLineInteraction interaction = new MarkerLineInteraction(marker, possible, playerPos, playerLook);
                if (interaction.didInteract()) {
                    best = interaction.getBetter(best);
                }
            }
        }
        if (best == null) {
            return false;
        }
        if (cache.tryConnect(best.marker1, best.marker2) || cache.tryConnect(best.marker2, best.marker1)) {
            if (cache instanceof VolumeSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_VOLUME_MARKER);
            } else if (cache instanceof PathSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PATH_MARKER);
            }
            return true;
        }
        return false;
    }

    public static boolean doesInteract(BlockPos a, BlockPos b, Player player) {
        return new MarkerLineInteraction(
            a,
            b,
            player.position().add(0, player.getEyeHeight(), 0),
            player.getLookAngle()
        ).didInteract();
    }

    private static InteractionResult onUseVolumeBoxes(Level level, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);

        VolumeBox currentEditing = volumeBoxes.getCurrentEditing(player);

        Vec3 start = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 end = start.add(player.getLookAngle().scale(4));

        if (player.isShiftKeyDown()) {
            if (currentEditing == null) {
                for (Iterator<VolumeBox> iterator = volumeBoxes.volumeBoxes.iterator(); iterator.hasNext();) {
                    VolumeBox volumeBox = iterator.next();
                    Optional<Vec3> clip = volumeBox.box.getBoundingBox().clip(start, end);
                    if (clip.isPresent()) {
                        if (volumeBox.getLockTargetsStream().noneMatch(Lock.Target.TargetResize.class::isInstance)) {
                            iterator.remove();
                            volumeBoxes.markDirtyAndBroadcast();
                            return InteractionResult.SUCCESS;
                        } else {
                            return InteractionResult.FAIL;
                        }
                    }
                }
            } else {
                if (currentEditing.hasSourceMarkers()) {
                    // Cancelling a box lifted off markers drops the box; the markers keep working.
                    volumeBoxes.volumeBoxes.remove(currentEditing);
                } else {
                    currentEditing.cancelEditing();
                }
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
        } else {
            if (currentEditing == null) {
                VolumeBox bestVolumeBox = null;
                double bestDist = Double.MAX_VALUE;
                BlockPos editing = null;

                for (VolumeBox volumeBox :
                    volumeBoxes.volumeBoxes.stream()
                        .filter(box ->
                            box.getLockTargetsStream()
                                .noneMatch(Lock.Target.TargetResize.class::isInstance)
                        )
                        .collect(Collectors.toList())
                    ) {
                    for (BlockPos p : PositionUtil.getCorners(volumeBox.box.min(), volumeBox.box.max())) {
                        Optional<Vec3> ray = new AABB(p).clip(start, end);
                        if (ray.isPresent()) {
                            double dist = ray.get().distanceTo(start);
                            if (bestDist > dist) {
                                bestDist = dist;
                                bestVolumeBox = volumeBox;
                                editing = p;
                            }
                        }
                    }
                }

                if (bestVolumeBox != null) {
                    bestVolumeBox.setPlayer(player);

                    BlockPos min = bestVolumeBox.box.min();
                    BlockPos max = bestVolumeBox.box.max();

                    BlockPos held = min;
                    if (editing.getX() == min.getX()) {
                        held = VecUtil.replaceValue(held, Direction.Axis.X, max.getX());
                    }
                    if (editing.getY() == min.getY()) {
                        held = VecUtil.replaceValue(held, Direction.Axis.Y, max.getY());
                    }
                    if (editing.getZ() == min.getZ()) {
                        held = VecUtil.replaceValue(held, Direction.Axis.Z, max.getZ());
                    }
                    bestVolumeBox.setHeldDistOldMinOldMax(
                        held,
                        Math.max(1.5, bestDist + 0.5),
                        bestVolumeBox.box.min(),
                        bestVolumeBox.box.max()
                    );
                    volumeBoxes.markDirtyAndBroadcast();
                    return InteractionResult.SUCCESS;
                }
            } else {
                if (currentEditing.hasSourceMarkers()) {
                    // The markers no longer match: they break as if a machine took them, and the box stays. If none
                    // were left to break (a machine took them, or they were broken by hand), the box goes too.
                    if (!currentEditing.sizeChangedWhileEditing() || currentEditing.breakSourceMarkers() == 0) {
                        // Same size as the markers (they already describe it), or nothing left to take it from.
                        volumeBoxes.volumeBoxes.remove(currentEditing);
                    }
                }
                currentEditing.confirmEditing();
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    /**
     * Server: the Marker Connector used on the volume marker at {@code markerPos}, hit at {@code hit}. While the player
     * drags a box this confirms it, as a click anywhere else does. Otherwise, when that marker is a corner of a
     * connected volume no box already covers or lifted, the markers' box is lifted into a one-shot {@link VolumeBox}
     * and resizing starts from that corner. Returns false when the click was neither, so the marker handles it.
     */
    public static boolean onUseOnVolumeMarker(Level level, Player player, BlockPos markerPos, Vec3 hit) {
        if (level.isClientSide() || player.isShiftKeyDown()
            || !(level.getBlockEntity(markerPos) instanceof TileMarkerVolume marker)) {
            return false;
        }
        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumeBox editing = volumeBoxes.getCurrentEditing(player);
        if (editing != null) {
            // Its own markers sit on the corners of a lifted box, so a click on one is a confirm; while dragging any
            // other box, a click on a marker still connects it.
            return editing.hasSourceMarkers() && onUseVolumeBoxes(level, player).consumesAction();
        }
        VolumeConnection connection = marker.getCurrentConnection();
        if (connection == null || !connection.getBox().isCorner(markerPos)) {
            return false;
        }
        if (volumeBoxes.getVolumeBoxAt(markerPos) != null
            || volumeBoxes.volumeBoxes.stream().anyMatch(box -> box.isBackedBy(markerPos))) {
            return false;
        }
        BlockPos min = connection.getBox().min();
        BlockPos max = connection.getBox().max();
        VolumeBox lifted = VolumeBox.fromMarkers(level, min, max, connection.getMarkerPositions());
        // Hold the corner opposite the clicked one, so the clicked corner follows the player's look.
        BlockPos held = new BlockPos(
            markerPos.getX() == min.getX() ? max.getX() : min.getX(),
            markerPos.getY() == min.getY() ? max.getY() : min.getY(),
            markerPos.getZ() == min.getZ() ? max.getZ() : min.getZ());
        // Drag at the middle of the look ray's run through the marker's block, so the corner starts on the marker. The
        // usual 1.5 floor would put it a block past a marker clicked from closer than that, and the hit point itself
        // can sit on the block's face.
        Vec3 eye = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 far = eye.add(player.getLookAngle().scale(hit.distanceTo(eye) + 2));
        AABB block = new AABB(markerPos);
        Optional<Vec3> entry = block.clip(eye, far);
        Optional<Vec3> exit = block.clip(far, eye);
        double dist = entry.isPresent() && exit.isPresent()
            ? (entry.get().distanceTo(eye) + exit.get().distanceTo(eye)) / 2
            : hit.distanceTo(eye) + 0.05;
        lifted.setPlayer(player);
        lifted.setHeldDistOldMinOldMax(held, dist, min, max);
        volumeBoxes.volumeBoxes.add(lifted);
        volumeBoxes.markDirtyAndBroadcast();
        return true;
    }

    @SuppressWarnings("WeakerAccess")
    private static class MarkerLineInteraction {
        public final BlockPos marker1, marker2;
        public final double distToPoint, distToLine;

        public MarkerLineInteraction(BlockPos marker1, BlockPos marker2, Vec3 playerPos, Vec3 playerEndPos) {
            this.marker1 = marker1;
            this.marker2 = marker2;
            LineSkewResult interactionPoint = PositionUtil.findLineSkewPoint(
                new Line(
                    VecUtil.convertCenter(marker1),
                    VecUtil.convertCenter(marker2)
                ),
                playerPos,
                playerEndPos
            );
            distToPoint = interactionPoint.closestPos.distanceTo(playerPos);
            distToLine = interactionPoint.distFromLine;
        }

        public boolean didInteract() {
            return distToPoint <= 3 && distToLine < 0.3;
        }

        public MarkerLineInteraction getBetter(MarkerLineInteraction other) {
            if (other == null) {
                return this;
            }
            if (other.marker1 == marker2 && other.marker2 == marker1) {
                return other;
            }
            if (other.distToLine < distToLine) {
                return other;
            }
            if (other.distToLine > distToLine) {
                return this;
            }
            if (other.distToPoint < distToPoint) {
                return other;
            }
            return this;
        }
    }
}
