/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.builders.client.render.AdvDebuggerQuarry;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.silicon.client.render.AdvDebuggerLaser;
import buildcraft.silicon.tile.TileLaser;

/**
 * Client-side render dispatcher for the advanced-debug overlay. Reads the current debug target
 * recorded by {@link buildcraft.lib.item.ItemDebugger} and draws the matching overlay during world
 * rendering. The whole feature is client-authoritative — no networking — so this is the only place
 * the overlay geometry is produced.
 */
public final class AdvDebugRenderer {
    private AdvDebugRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        BlockPos target = BCAdvDebugging.INSTANCE.getClientTarget();
        if (target == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        BlockEntity be = mc.level.getBlockEntity(target);
        if (!(be instanceof IAdvDebugTarget)) {
            // Tile gone (broken, unloaded, swapped) — stop drawing.
            BCAdvDebugging.INSTANCE.clear();
            return;
        }

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        if (be instanceof TileQuarry quarry) {
            AdvDebuggerQuarry.render(quarry, poseStack, bufferSource, cameraPos);
        } else if (be instanceof TileLaser laser) {
            AdvDebuggerLaser.render(laser, poseStack, bufferSource, cameraPos);
        }

        bufferSource.endBatch(RenderTypes.lines());
    }
}
