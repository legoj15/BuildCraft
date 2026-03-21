/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import buildcraft.api.tiles.IDebuggable;

/**
 * Appends IDebuggable info from the targeted block entity to the F3 debug screen.
 * Register on the NeoForge event bus (game bus, not mod bus).
 */
public class BCDebugOverlay {

    @SubscribeEvent
    public static void onRenderDebugOverlay(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay() == null || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        if (mc.level == null || mc.hitResult == null) {
            return;
        }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getDirection();

        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof IDebuggable debuggable) {
            List<String> left = new ArrayList<>();
            List<String> right = new ArrayList<>();
            debuggable.getDebugInfo(left, right, side);
            debuggable.getClientDebugInfo(left, right, side);

            // The info will be rendered by Minecraft's debug screen
            // We add it via the overlay's game info list
            // Since RenderGuiLayerEvent.Post fires after the debug screen renders,
            // we store it for display on the next frame via the debug screen's system info
            // Actually, we need to render it ourselves
            if (!left.isEmpty() || !right.isEmpty()) {
                renderDebugText(event, left, right);
            }
        }
    }

    private static void renderDebugText(RenderGuiLayerEvent.Post event, List<String> left, List<String> right) {
        Minecraft mc = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int fontHeight = mc.font.lineHeight;

        // Render left-side debug info at bottom-left
        int y = screenHeight - 2 - (left.size() * (fontHeight + 1));
        for (String line : left) {
            graphics.drawString(mc.font, "[BC] " + line, 2, y, 0xFFFFFF);
            y += fontHeight + 1;
        }

        // Render right-side debug info at bottom-right
        y = screenHeight - 2 - (right.size() * (fontHeight + 1));
        for (String line : right) {
            int x = screenWidth - mc.font.width("[BC] " + line) - 2;
            graphics.drawString(mc.font, "[BC] " + line, x, y, 0xFFFFFF);
            y += fontHeight + 1;
        }
    }
}
