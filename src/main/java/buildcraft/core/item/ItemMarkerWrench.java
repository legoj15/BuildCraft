/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.item;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSegment;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.VecUtil;

import buildcraft.core.marker.PathSubCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.tile.TileMarkerVolume;

/**
 * The second wrench tier: everything the {@link ItemWrench_Neptune basic wrench} does, plus linking marker lasers,
 * resizing a connected marker volume, and removing or confirming volume boxes.
 * <p>
 * <b>Click priority.</b> A marker laser hangs in the air, so the block behind the crosshair is usually the ground,
 * and the wrench would rotate that instead. {@link #isAimingAtMarkerTarget} tells whether the click is meant for a
 * marker target (a laser, a box to remove, a box being edited, or a corner this wrench may drag);
 * {@link #onItemUseFirst} then does the marker work itself and consumes the click, so neither the wrench rotation
 * nor the block's own use runs (a chest or machine behind the laser would otherwise open its screen: the wrench
 * lets sneak-clicks through to blocks). The server does the work; the client only answers with the same success,
 * which keeps it from also sending a {@code use}. A click in the air goes to {@link #use} as before; on the server
 * {@code use} acts only when no block lies within the player's reach, so a block click (one whose target lay behind
 * the block included) is never acted on a second time or through the block.
 * Anything else keeps the inherited wrench behaviour. The client runs the same test on its own marker caches and box
 * list.
 * <p>
 * <b>Dragging.</b> Picking up a volume box's corner in the air is gated by {@link #canEditCorners()}; only the
 * {@link ItemVolumeWrench} turns it on. The one drag every marker wrench has is the resize of a connected volume
 * ({@link #onUseOnVolumeMarker}): clicking one of its corner markers lifts the markers into a one-shot
 * {@link VolumeBox} and drags that corner. Confirming with a new size breaks the markers and leaves the floating box
 * for the next machine placed against it; confirming the same size, or cancelling, drops the box and the markers keep
 * working.
 */
public class ItemMarkerWrench extends ItemWrench_Neptune {

    private static final Identifier ADVANCEMENT_VOLUME_MARKER = Identifier.parse("buildcraftunofficial:markers");
    private static final Identifier ADVANCEMENT_PATH_MARKER = Identifier.parse("buildcraftunofficial:path_markers");

    /** How far along the view ray a laser, box or corner can be picked. */
    private static final double REACH = 4;
    /** How far beyond the clicked block's hit point a target may lie and still count as in front of that block. */
    private static final double OCCLUSION_SLACK = 0.1;

    public ItemMarkerWrench(Item.Properties properties) {
        super(properties);
    }

    /** Whether this wrench may pick up a volume box's corner to drag it. Confirming or cancelling a drag in
     *  progress, removing a box and lifting a connected marker volume are open to every marker wrench. */
    protected boolean canEditCorners() {
        return false;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && isAimingAtMarkerTarget(context.getLevel(), player, context.getClickLocation())
                && !leavesClickToMarker(context.getLevel(), player, context.getClickedPos())) {
            Level level = context.getLevel();
            if (!level.isClientSide()) {
                doMarkerClick(level, player, context.getClickLocation());
            }
            return InteractionResult.SUCCESS;
        }
        return super.onItemUseFirst(stack, context);
    }

    /**
     * Whether a click on the block at {@code clicked} is left to that block while the player drags a box lifted off
     * markers: a volume marker that does not back the box connects as it does with no drag under way, and only the
     * box's own markers, or a click anywhere else, confirm it. Crouching cancels wherever it lands. Any other drag
     * takes every click.
     */
    private static boolean leavesClickToMarker(Level level, Player player, BlockPos clicked) {
        if (player.isShiftKeyDown() || !(level.getBlockEntity(clicked) instanceof TileMarkerVolume)) {
            return false;
        }
        VolumeBox editing = editingBox(volumeBoxesOf(level), player);
        return editing != null && editing.hasSourceMarkers() && !editing.isBackedBy(clicked);
    }

    @Override
    //? if >=1.21.10 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useImpl(level, player);
    }
    //?} else {
    /*// 1.21.1: Item.use returns InteractionResultHolder<ItemStack>. Wrap the shared InteractionResult
    // logic (useImpl) with the held stack — InteractionResult.SUCCESS is version-neutral.
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(useImpl(level, player), player.getItemInHand(hand));
    }*/
    //?}

    private InteractionResult useImpl(Level level, Player player) {
        // Only a true air click is this method's: a click that met a block within reach belongs to onItemUseFirst,
        // which acted on it if it was meant for a marker target. A block click whose target lay behind the block
        // also lands here (onItemUseFirst, the block and useOn all passed) and must not reach through the block; a
        // client that still sends a use for a click onItemUseFirst handled must not act twice. Both are kept out by
        // this one rule. Past block reach nothing can hide a target, which lies within REACH, so no occlusion limit.
        if (!level.isClientSide() && !hitsBlock(level, player)) {
            doMarkerClick(level, player, null);
        }
        // Always consume the action so the player gets swing feedback
        return InteractionResult.SUCCESS;
    }

    /** Whether the view ray meets a block within the player's block interaction range: the click was a block click. */
    private static boolean hitsBlock(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye,
            eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    /** How far along the view ray a target may lie and still be in front of the block hit at {@code clickLocation}
     *  (unlimited when no block was hit). */
    private static double occlusionLimit(Vec3 eye, @Nullable Vec3 clickLocation) {
        return clickLocation == null ? Double.POSITIVE_INFINITY : eye.distanceTo(clickLocation) + OCCLUSION_SLACK;
    }

    /**
     * The marker work of a click, server side: a drag in progress is confirmed or cancelled wherever the click lands;
     * otherwise the aimed laser is linked, else the aimed box target is acted on. Targets farther than the block at
     * {@code clickLocation} are behind it and are left alone (the same rule as {@link #isAimingAtMarkerTarget}).
     */
    private void doMarkerClick(Level level, Player player, @Nullable Vec3 clickLocation) {
        if (level.isClientSide()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double limit = occlusionLimit(eye, clickLocation);
        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumePick pick = pickVolume(volumeBoxes.volumeBoxes, player);
        if (pick.kind().editInProgress) {
            applyVolumePick(volumeBoxes, pick, player);
            return;
        }
        for (MarkerCache<?> cache : MarkerCache.CACHES) {
            if (connectAimedLine(cache.getSubCache(level), player, eye, look, Math.min(REACH, limit))) {
                return;
            }
        }
        if (pick.kind().claimsClick && pick.distance() <= limit) {
            applyVolumePick(volumeBoxes, pick, player);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Click priority

    /**
     * True if a click while aiming like this is meant for a marker target rather than the block behind it. Works on
     * either side: the server reads its saved boxes, the client its synced copy, and both read the marker caches of
     * their own level.
     *
     * @param clickLocation where the click hit the block; a target farther away than that is behind the block, so
     *            the block is what the player is aiming at. {@code null} skips that check. A box this player is
     *            mid-edit on takes every click wherever it lands: the click confirms or cancels the edit.
     */
    public boolean isAimingAtMarkerTarget(Level level, Player player, @Nullable Vec3 clickLocation) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double limit = occlusionLimit(eye, clickLocation);

        VolumePick pick = pickVolume(volumeBoxesOf(level), player);
        if (pick.kind().editInProgress) {
            return true;
        }
        if (pick.kind().claimsClick && pick.distance() <= limit) {
            return true;
        }
        double lineReach = Math.min(REACH, limit);
        for (MarkerCache<?> cache : MarkerCache.CACHES) {
            if (aimedLine(cache.getSubCache(level), eye, look, lineReach) != null) {
                return true;
            }
        }
        return false;
    }

    private static List<VolumeBox> volumeBoxesOf(Level level) {
        return level.isClientSide() ? ClientVolumeBoxes.INSTANCE.volumeBoxes
            : LevelSavedDataVolumeBoxes.get(level).volumeBoxes;
    }

    /** The box {@code player} is dragging, or null. */
    @Nullable
    private static VolumeBox editingBox(List<VolumeBox> boxes, Player player) {
        for (VolumeBox box : boxes) {
            if (box.isEditingBy(player)) {
                return box;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Marker lasers

    /** The laser of a connection that could be made which the view ray passes closest to, if it passes close enough. */
    @Nullable
    private static MarkerSegment.Aim aimedLine(MarkerSubCache<?> cache, Vec3 eye, Vec3 look, double reach) {
        MarkerSegment.Aim best = null;
        // A connection is offered from both of its ends; each laser is tried once.
        Set<MarkerSegment> seen = new HashSet<>();
        for (BlockPos marker : cache.getAllMarkers()) {
            for (BlockPos possible : cache.getValidConnections(marker)) {
                MarkerSegment segment = new MarkerSegment(marker, possible);
                if (!seen.add(segment)) {
                    continue;
                }
                MarkerSegment.Aim aim = segment.aim(eye, look, reach);
                if (aim != null && (best == null || aim.score() < best.score())) {
                    best = aim;
                }
            }
        }
        return best;
    }

    private static boolean connectAimedLine(MarkerSubCache<?> cache, Player player, Vec3 eye, Vec3 look,
                                            double reach) {
        MarkerSegment.Aim best = aimedLine(cache, eye, look, reach);
        if (best == null) {
            return false;
        }
        BlockPos a = best.segment().from();
        BlockPos b = best.segment().to();
        if (cache.tryConnect(a, b) || cache.tryConnect(b, a)) {
            if (cache instanceof VolumeSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_VOLUME_MARKER);
            } else if (cache instanceof PathSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PATH_MARKER);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Volume boxes

    /** What a click does to the volume boxes, given where the player is aiming. */
    private enum VolumeClick {
        /** Nothing here for this wrench. A click aimed at a locked box lands here too: the wrench does not claim it,
         *  so it passes on to the block (or does nothing in the air). */
        NOTHING(false, false),
        BOX_REMOVE(true, false),
        CORNER_DRAG(true, false),
        /** The click ends this player's drag, keeping the new size. Applies wherever the click lands. */
        EDIT_CONFIRM(true, true),
        /** The click ends this player's drag, restoring the old size. Applies wherever the click lands. */
        EDIT_CANCEL(true, true);

        /** The wrench acts on the box list for this click. */
        final boolean claimsClick;
        /** The click acts on a box this player is dragging, so where it lands does not matter. */
        final boolean editInProgress;

        VolumeClick(boolean claimsClick, boolean editInProgress) {
            this.claimsClick = claimsClick;
            this.editInProgress = editInProgress;
        }
    }

    /**
     * @param box the box acted on.
     * @param corner the corner block, for {@link VolumeClick#CORNER_DRAG}.
     * @param distance how far along the view ray the target lies; 0 for the edit clicks.
     */
    private record VolumePick(VolumeClick kind, @Nullable VolumeBox box, @Nullable BlockPos corner, double distance) {
        static final VolumePick NOTHING = new VolumePick(VolumeClick.NOTHING, null, null, 0);
    }

    /**
     * Decides what a click does to {@code boxes}. The single source of truth for the click itself and for
     * {@link #isAimingAtMarkerTarget}, so the two cannot disagree. Targets are picked by distance, never by list
     * order, because the client's list is not in the server's order.
     */
    private VolumePick pickVolume(List<VolumeBox> boxes, Player player) {
        VolumeBox currentEditing = editingBox(boxes, player);
        // A drag in progress takes the click before anything it may be aimed at: crouching cancels it, otherwise it
        // is confirmed.
        if (currentEditing != null) {
            return new VolumePick(player.isShiftKeyDown() ? VolumeClick.EDIT_CANCEL : VolumeClick.EDIT_CONFIRM,
                currentEditing, null, 0);
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(REACH));

        if (player.isShiftKeyDown()) {
            VolumeBox nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (VolumeBox box : boxes) {
                Optional<Vec3> clip = box.box.getBoundingBox().clip(start, end);
                if (clip.isPresent() && nearestDist > clip.get().distanceTo(start)) {
                    nearestDist = clip.get().distanceTo(start);
                    nearest = box;
                }
            }
            // A box a machine holds on to (Remove), or uses as it is (Resize), stays. A box a machine owns is
            // locked against removal only: it goes with its machine, never by a wrench. A box another player is
            // dragging stays with that player, whose drag the removal would orphan.
            if (nearest != null && !nearest.isEditing() && nearest.getLockTargetsStream().noneMatch(
                target -> target instanceof Lock.Target.TargetRemove || target instanceof Lock.Target.TargetResize)) {
                return new VolumePick(VolumeClick.BOX_REMOVE, nearest, null, nearestDist);
            }
            return VolumePick.NOTHING;
        }

        if (!canEditCorners()) {
            return VolumePick.NOTHING;
        }
        VolumeBox bestBox = null;
        BlockPos bestCorner = null;
        double bestDist = Double.MAX_VALUE;
        for (VolumeBox box : boxes) {
            // A box being dragged (by anyone) stays with its dragger; a resize-locked one cannot be dragged at all.
            if (box.isEditing() || box.getLockTargetsStream().anyMatch(Lock.Target.TargetResize.class::isInstance)) {
                continue;
            }
            for (BlockPos p : PositionUtil.getCorners(box.box.min(), box.box.max())) {
                Optional<Vec3> ray = new AABB(p).clip(start, end);
                if (ray.isPresent()) {
                    double dist = ray.get().distanceTo(start);
                    if (bestDist > dist) {
                        bestDist = dist;
                        bestBox = box;
                        bestCorner = p;
                    }
                }
            }
        }
        if (bestBox == null) {
            return VolumePick.NOTHING;
        }
        return new VolumePick(VolumeClick.CORNER_DRAG, bestBox, bestCorner, bestDist);
    }

    /** Carries out {@code pick} on the server's boxes. */
    private void applyVolumePick(LevelSavedDataVolumeBoxes volumeBoxes, VolumePick pick, Player player) {
        VolumeBox box = pick.box();
        switch (pick.kind()) {
            case BOX_REMOVE -> {
                volumeBoxes.volumeBoxes.remove(box);
                volumeBoxes.markDirtyAndBroadcast();
            }
            case EDIT_CANCEL -> {
                if (box.hasSourceMarkers()) {
                    // Cancelling a box lifted off markers drops the box; the markers keep working.
                    volumeBoxes.volumeBoxes.remove(box);
                } else {
                    box.cancelEditing();
                }
                volumeBoxes.markDirtyAndBroadcast();
            }
            case EDIT_CONFIRM -> {
                // A box lifted off markers whose size changed breaks them as if a machine took them, and stays. If
                // none were left to break (a machine took them, or they were broken by hand), or the size is the one
                // the markers already describe, the box goes too.
                if (box.hasSourceMarkers() && (!box.sizeChangedWhileEditing() || box.breakSourceMarkers() == 0)) {
                    volumeBoxes.volumeBoxes.remove(box);
                }
                box.confirmEditing();
                volumeBoxes.markDirtyAndBroadcast();
            }
            case CORNER_DRAG -> {
                box.setPlayer(player);

                BlockPos min = box.box.min();
                BlockPos max = box.box.max();
                BlockPos editing = pick.corner();

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
                box.setHeldDistOldMinOldMax(held, Math.max(1.5, pick.distance() + 0.5), box.box.min(), box.box.max());
                volumeBoxes.markDirtyAndBroadcast();
            }
            default -> {
                // NOTHING: the wrench did not claim this click
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Resizing a connected marker volume

    /**
     * Server: a marker wrench used on the volume marker at {@code markerPos}, hit at {@code hit}. When that marker is
     * a corner of a connected volume that no box already covers or lifted, the markers' box is lifted into a one-shot
     * {@link VolumeBox} and resizing starts from that corner. Returns false when the click was not that, so the marker
     * handles it. (A click that confirms or cancels a drag in progress never gets here: {@link #onItemUseFirst} takes
     * it, except a click on a marker that does not back the dragged box, which is left to connect.)
     */
    public static boolean onUseOnVolumeMarker(Level level, Player player, BlockPos markerPos, Vec3 hit) {
        if (level.isClientSide() || player.isShiftKeyDown()
            || !(level.getBlockEntity(markerPos) instanceof TileMarkerVolume marker)) {
            return false;
        }
        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        if (editingBox(volumeBoxes.volumeBoxes, player) != null) {
            return false;
        }
        VolumeConnection connection = marker.getCurrentConnection();
        if (connection == null || !connection.getBox().isCorner(markerPos)) {
            return false;
        }
        if (volumeBoxes.isReservedMarker(marker)) {
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
        Vec3 eye = player.getEyePosition();
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
}
