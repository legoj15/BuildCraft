/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

// 1.21.1-ONLY: the modern nodes (>=1.21.10) render this map through the 1.21.5+ offscreen PiP pipeline
// (ZoneMapPipRenderer). That pipeline doesn't exist on 1.21.1, so here we draw the SAME geometry
// (ZoneMapGeometry) straight into the GUI buffer source with a real 3D PoseStack — on 1.21.1
// GuiGraphics.pose() IS a com.mojang.blaze3d.vertex.PoseStack, not the 2D Matrix3x2fStack of 26.1 —
// mirroring BlueprintGuiRenderer. The whole file is gated out on >=1.21.10. Unlike the blueprint preview
// this needs no Y-flip (the camera already emits down-positive canvas Y), no lighting and no textures
// (terrain is flat POSITION_COLOR quads with per-face shade baked into the vertex colour).
//? if <1.21.10 {
/*import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.zone.ZonePlan;

public final class ZoneMapGuiRenderer {

    private ZoneMapGuiRenderer() {}

    // GUI depth the map's ground plane sits at; the small per-column depth spread is layered around it.
    // Mirrors BlueprintGuiRenderer.CENTER_DEPTH — keeps the map inside the GUI's render depth band.
    private static final float CENTER_DEPTH = 100.0f;

    // Entry point from GuiZonePlanner.submitViewport on 1.21.1. Draws the terrain + zone overlays clipped
    // to the given GUI-pixel viewport, replicating what the PiP base class does for the offscreen texture:
    // origin at the viewport centre, canvas (ground-block) units scaled by pxPerBlock into GUI pixels.
    public static void render(BCGraphics graphics, ZoneMapCamera camera, BlockPos tilePos,
                              ZonePlan[] layers, ZonePlan bufferLayer, int bufferColorIndex,
                              BlockPos hoverPos, int viewportX, int viewportY, int viewportW, int viewportH) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        PoseStack pose = graphics.raw.pose();
        MultiBufferSource.BufferSource buffers = graphics.raw.bufferSource();

        // Clip to the map panel, and give the 3D draw a clean depth slate inside it (the GUI panel has
        // already written depth here in the background phase). glClear honours the GL scissor box, so only
        // the map panel's depth is touched.
        graphics.raw.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        pose.pushPose();
        // Origin at the viewport centre, pushed into the GUI depth band. scale(px, px, -px): camera canvas
        // units are world blocks at the ground plane, so pxPerBlock converts them to GUI pixels; the -Z
        // matches the offscreen PiP base class's scale(s, s, -s) so per-column depth sorts the same way.
        // (If terrain relief sorts inverted in-client, flip this Z sign.)
        float px = (float) camera.pxPerBlock;
        pose.translate(viewportX + viewportW / 2.0f, viewportY + viewportH / 2.0f, CENTER_DEPTH);
        pose.scale(px, px, -px);
        RenderSystem.enableDepthTest();

        Matrix4f mat = pose.last().pose();
        ZoneMapGeometry.emitTerrain(buffers.getBuffer(BCLibRenderTypes.debugSolid()), mat, camera,
                viewportW, viewportH, level);
        ZoneMapGeometry.emitOverlays(buffers.getBuffer(BCLibRenderTypes.debugFilled()), mat, camera,
                tilePos, layers, bufferLayer, bufferColorIndex, hoverPos, level);
        buffers.endBatch();

        pose.popPose();
        // Leave the map panel's depth clean for the foreground GUI (slots/items/labels) drawn after this
        // background phase, so the map's terrain depth can't reject them.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        graphics.raw.disableScissor();
    }
}*/
//?}
