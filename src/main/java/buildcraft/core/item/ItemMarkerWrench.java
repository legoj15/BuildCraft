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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSegment;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.VecUtil;

import buildcraft.core.marker.PathSubCache;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.marker.volume.Addon;
import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.EnumAddonSlot;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;

/**
 * The second wrench tier: everything the {@link ItemWrench_Neptune basic wrench} does, plus linking marker lasers,
 * editing volume boxes' addons, and removing or confirming boxes.
 * <p>
 * <b>Click priority.</b> A marker laser hangs in the air, so the block behind the crosshair is usually the ground,
 * and the wrench would rotate that instead. {@link #isAimingAtMarkerTarget} tells whether the click is meant for a
 * marker target (a laser, an addon slot, a box to remove, a box being edited, or a corner this wrench may drag);
 * {@link #onItemUseFirst} and {@link #useOn} then pass, so vanilla falls through to {@link #use}, which does the
 * marker work. Anything else keeps the inherited wrench behaviour. The client runs the same test on its own
 * marker caches and box list, because it decides whether to send the {@code use} at all.
 * <p>
 * Dragging a box corner is gated by {@link #canEditCorners()}; only the {@link ItemVolumeWrench} turns it on.
 */
public class ItemMarkerWrench extends ItemWrench_Neptune {

    private static final Identifier ADVANCEMENT_VOLUME_MARKER = Identifier.parse("buildcraftunofficial:markers");
    private static final Identifier ADVANCEMENT_PATH_MARKER = Identifier.parse("buildcraftunofficial:path_markers");

    /** How far along the view ray a laser, addon slot, box or corner can be picked. */
    private static final double REACH = 4;
    /** How far beyond the clicked block's hit point a target may lie and still count as in front of that block. */
    private static final double OCCLUSION_SLACK = 0.1;

    public ItemMarkerWrench(Item.Properties properties) {
        super(properties);
    }

    /** Whether this wrench may pick up a volume box's corner to drag it. Confirming or cancelling a drag in
     *  progress, using addons and removing a box are open to every marker wrench. */
    protected boolean canEditCorners() {
        return false;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (claimsClick(context)) {
            return InteractionResult.PASS;
        }
        return super.onItemUseFirst(stack, context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (claimsClick(context)) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }

    private boolean claimsClick(UseOnContext context) {
        Player player = context.getPlayer();
        return player != null && isAimingAtMarkerTarget(context.getLevel(), player, context.getClickLocation());
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
        if (!level.isClientSide()) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                if (connectAimedLine(cache.getSubCache(level), player, eye, look)) {
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
        double limit = clickLocation == null ? Double.POSITIVE_INFINITY
            : eye.distanceTo(clickLocation) + OCCLUSION_SLACK;

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

    private static boolean connectAimedLine(MarkerSubCache<?> cache, Player player, Vec3 eye, Vec3 look) {
        MarkerSegment.Aim best = aimedLine(cache, eye, look, REACH);
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
        /** Nothing here for this wrench. A click aimed at a locked box lands here too: it is eaten, nothing happens. */
        NOTHING(false, false),
        ADDON_USE(true, false),
        ADDON_REMOVE(true, false),
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
     * @param slot the addon slot, for the addon clicks.
     * @param corner the corner block, for {@link VolumeClick#CORNER_DRAG}.
     * @param distance how far along the view ray the target lies; 0 for the edit clicks.
     */
    private record VolumePick(VolumeClick kind, @Nullable VolumeBox box, @Nullable EnumAddonSlot slot,
        @Nullable BlockPos corner, double distance) {
        static final VolumePick NOTHING = new VolumePick(VolumeClick.NOTHING, null, null, null, 0);
    }

    /**
     * Decides what a click does to {@code boxes}. The single source of truth for the click itself and for
     * {@link #isAimingAtMarkerTarget}, so the two cannot disagree. Targets are picked by distance, never by list
     * order, because the client's list is not in the server's order.
     */
    private VolumePick pickVolume(List<VolumeBox> boxes, Player player) {
        VolumeBox currentEditing = null;
        for (VolumeBox box : boxes) {
            if (box.isEditingBy(player)) {
                currentEditing = box;
                break;
            }
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(REACH));

        EnumAddonSlot addonSlot = null;
        VolumeBox addonBox = null;
        double addonDist = Double.MAX_VALUE;
        for (VolumeBox box : boxes) {
            for (EnumAddonSlot slot : EnumAddonSlot.VALUES) {
                Optional<Vec3> ray = slot.getBoundingBox(box).clip(start, end);
                if (ray.isPresent()) {
                    double dist = ray.get().distanceTo(start);
                    if (addonDist > dist) {
                        addonDist = dist;
                        addonBox = box;
                        addonSlot = slot;
                    }
                }
            }
        }
        if (addonBox != null) {
            final EnumAddonSlot slot = addonSlot;
            if (addonBox.addons.containsKey(slot) && addonBox.getLockTargetsStream().noneMatch(
                target -> target instanceof Lock.Target.TargetAddon && ((Lock.Target.TargetAddon) target).slot == slot)) {
                return new VolumePick(player.isShiftKeyDown() ? VolumeClick.ADDON_REMOVE : VolumeClick.ADDON_USE,
                    addonBox, slot, null, addonDist);
            }
            return VolumePick.NOTHING;
        }

        if (player.isShiftKeyDown()) {
            if (currentEditing != null) {
                return new VolumePick(VolumeClick.EDIT_CANCEL, currentEditing, null, null, 0);
            }
            VolumeBox nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (VolumeBox box : boxes) {
                Optional<Vec3> clip = box.box.getBoundingBox().clip(start, end);
                if (clip.isPresent() && nearestDist > clip.get().distanceTo(start)) {
                    nearestDist = clip.get().distanceTo(start);
                    nearest = box;
                }
            }
            if (nearest != null && nearest.getLockTargetsStream().noneMatch(Lock.Target.TargetResize.class::isInstance)) {
                return new VolumePick(VolumeClick.BOX_REMOVE, nearest, null, null, nearestDist);
            }
            return VolumePick.NOTHING;
        }

        if (currentEditing != null) {
            return new VolumePick(VolumeClick.EDIT_CONFIRM, currentEditing, null, null, 0);
        }
        if (!canEditCorners()) {
            return VolumePick.NOTHING;
        }
        VolumeBox bestBox = null;
        BlockPos bestCorner = null;
        double bestDist = Double.MAX_VALUE;
        for (VolumeBox box : boxes) {
            if (box.getLockTargetsStream().anyMatch(Lock.Target.TargetResize.class::isInstance)) {
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
        return new VolumePick(VolumeClick.CORNER_DRAG, bestBox, null, bestCorner, bestDist);
    }

    private InteractionResult onUseVolumeBoxes(Level level, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        LevelSavedDataVolumeBoxes volumeBoxes = LevelSavedDataVolumeBoxes.get(level);
        VolumePick pick = pickVolume(volumeBoxes.volumeBoxes, player);
        VolumeBox box = pick.box();
        switch (pick.kind()) {
            case ADDON_USE -> {
                box.addons.get(pick.slot()).onPlayerRightClick(player);
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
            case ADDON_REMOVE -> {
                box.addons.get(pick.slot()).onRemoved();
                box.addons.remove(pick.slot());
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
            case BOX_REMOVE -> {
                box.addons.values().forEach(Addon::onRemoved);
                volumeBoxes.volumeBoxes.remove(box);
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
            case EDIT_CANCEL -> {
                box.cancelEditing();
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
            }
            case EDIT_CONFIRM -> {
                box.confirmEditing();
                volumeBoxes.markDirtyAndBroadcast();
                return InteractionResult.SUCCESS;
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
                return InteractionResult.SUCCESS;
            }
            default -> {
                return InteractionResult.FAIL;
            }
        }
    }
}
