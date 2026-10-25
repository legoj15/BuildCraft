/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSegment;
import buildcraft.lib.marker.MarkerSubCache;

/**
 * Shows the length of the marker laser under the crosshair, just below it. Connected lasers always count; the
 * thin "possible connection" previews count only while a marker connector is held, the same time they are drawn.
 * The pick runs once a client tick; the GUI layer only draws the result.
 */
public final class MarkerLengthIndicator {
    /** How far away a laser can be read. Lasers are visible much further, but past this the pick gets fiddly. */
    private static final double MAX_DISTANCE = 64;
    /** See-through blocks (leaves and the like) a sight line may pass before it counts as blocked. */
    private static final int MAX_SEE_THROUGH = 8;
    private static final int COLOUR_CONNECTED = 0xFFFFFFFF;
    private static final int COLOUR_POSSIBLE = 0xFFA0A0A0;

    private static Component label;
    private static boolean possible;

    private MarkerLengthIndicator() {}

    /** Called from ClientTickEvent.Post. */
    public static void onClientTick() {
        label = null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // Off first person the crosshair is gone and the eye ray is not the screen centre.
        if (player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Every segment is tried once: connected lasers first, so a preview never shadows the real one.
        Set<MarkerSegment> seen = new HashSet<>();
        List<MarkerSegment.Aim> aims = new ArrayList<>();
        Set<MarkerSegment> possibleAims = new HashSet<>();
        for (MarkerCache<? extends MarkerSubCache<?>> cache : MarkerCache.CACHES) {
            for (MarkerConnection<?> connection : cache.getSubCache(mc.level).getConnections()) {
                for (MarkerSegment segment : connection.getSegments()) {
                    tryAim(segment, eye, look, seen, aims);
                }
            }
        }
        if (MarkerRenderer.isHoldingConnector(player)) {
            for (MarkerCache<? extends MarkerSubCache<?>> cache : MarkerCache.CACHES) {
                MarkerSubCache<?> subCache = cache.getSubCache(mc.level);
                if (subCache.getPossibleLaserType() == null) {
                    continue;
                }
                for (BlockPos marker : subCache.getAllMarkers()) {
                    for (BlockPos target : subCache.getValidConnections(marker)) {
                        MarkerSegment.Aim aim = tryAim(new MarkerSegment(marker, target), eye, look, seen, aims);
                        if (aim != null) {
                            possibleAims.add(aim.segment());
                        }
                    }
                }
            }
        }
        // Best first; a laser hidden behind a block gives way to the next one the player can see.
        aims.sort(Comparator.comparingDouble(MarkerSegment.Aim::score));
        for (MarkerSegment.Aim aim : aims) {
            if (!isHidden(mc.level, player, eye, aim)) {
                label = Component.translatable("buildcraft.marker.length", formatSpans(aim.segment().spans()));
                possible = possibleAims.contains(aim.segment());
                return;
            }
        }
    }

    @Nullable
    private static MarkerSegment.Aim tryAim(MarkerSegment segment, Vec3 eye, Vec3 look, Set<MarkerSegment> seen,
            List<MarkerSegment.Aim> aims) {
        if (!seen.add(segment)) {
            return null;
        }
        MarkerSegment.Aim aim = segment.aim(eye, look, MAX_DISTANCE);
        if (aim != null) {
            aims.add(aim);
        }
        return aim;
    }

    /** True if an opaque block sits between the eye and the aimed point. A VISUAL clip already passes through
     *  markers and glass (no visual shape); leaves-like blocks are hit but don't occlude, so the clip resumes
     *  just past each one until it reaches an occluding block or the laser. */
    private static boolean isHidden(Level level, LocalPlayer player, Vec3 eye, MarkerSegment.Aim aim) {
        Vec3 target = aim.point();
        Vec3 step = target.subtract(eye).normalize().scale(0.01);
        Vec3 from = eye;
        for (int i = 0; i <= MAX_SEE_THROUGH; i++) {
            BlockHitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return false;
            }
            BlockPos pos = hit.getBlockPos();
            if (level.getBlockState(pos).canOcclude()) {
                return true;
            }
            // Resume from where the ray leaves this block.
            from = exitPoint(pos, hit.getLocation(), step);
            if (from == null) {
                return true;
            }
            if (from.distanceToSqr(eye) >= target.distanceToSqr(eye)) {
                return false;
            }
        }
        // A canopy this deep is as good as a wall.
        return true;
    }

    /** Where a ray stepping by {@code step} (0.01 blocks) from {@code entry} first leaves block {@code pos}, or
     *  null if it never does within one block's diagonal. */
    @Nullable
    private static Vec3 exitPoint(BlockPos pos, Vec3 entry, Vec3 step) {
        Vec3 p = entry;
        // 180 steps of 0.01 = 1.8 blocks, just over a block's sqrt(3) diagonal.
        for (int i = 0; i < 180; i++) {
            p = p.add(step);
            if (!BlockPos.containing(p).equals(pos)) {
                return p;
            }
        }
        return null;
    }

    /** "12" for a straight line along one axis; "5 × 3" (each axis it spans) for a diagonal path leg. */
    static String formatSpans(int[] spans) {
        StringJoiner joined = new StringJoiner(" × ");
        for (int span : spans) {
            if (span > 1) {
                joined.add(Integer.toString(span));
            }
        }
        return joined.length() == 0 ? "1" : joined.toString();
    }

    //? if >=26.1 {
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor vanillaGraphics, DeltaTracker deltaTracker) {
    //?} else {
    /*public static void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, DeltaTracker deltaTracker) {*/
    //?}
        Component text = label;
        Minecraft mc = Minecraft.getInstance();
        // A layer registered by event has no visibility condition of its own, so F1 must be checked here.
        //? if >=26.2 {
        boolean hudHidden = mc.gui.hud.isHidden();
        //?} else {
        /*boolean hudHidden = mc.options.hideGui;*/
        //?}
        if (text == null || hudHidden) {
            return;
        }
        Font font = mc.font;
        int x = (mc.getWindow().getGuiScaledWidth() - font.width(text)) / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 10;
        new BCGraphics(vanillaGraphics).text(font, text, x, y, possible ? COLOUR_POSSIBLE : COLOUR_CONNECTED, true);
    }
}
