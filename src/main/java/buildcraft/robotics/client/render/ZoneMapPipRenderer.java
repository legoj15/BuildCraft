/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

// Whole-file >=1.21.10: this renderer is built on the 1.21.5+ offscreen PiP pipeline, which doesn't
// exist on 1.21.1 — so the class is gated out there. The 1.21.1 line renders the SAME map straight into
// the GUI via ZoneMapGuiRenderer instead; both paths share ZoneMapGeometry for the vertices. Cross-node
// plumbing mirrors BlueprintPipRenderer: a 26.2 sub-cliff swaps the BufferSource constructor + endBatch
// flush for the SubmitNodeCollector "submit" model.
//? if >=1.21.10 {
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;*/
//?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.BCLibRenderTypes;

/**
 * Paints the Zone Planner's terrain map into an offscreen texture; the base class blits it into the GUI.
 * Terrain comes from the client's own loaded chunks (see {@link buildcraft.robotics.client.zone.ZonePlannerMapChunk});
 * the camera/projection ({@link buildcraft.robotics.client.zone.ZoneMapCamera}) is sampled fresh from the
 * render state each frame, so pan/zoom/paint are simply different states submitted by the GUI. The actual
 * vertex geometry lives in {@link ZoneMapGeometry}, shared with the 1.21.1 direct-to-GUI path
 * ({@code ZoneMapGuiRenderer}).
 */
public class ZoneMapPipRenderer extends PictureInPictureRenderer<ZoneMapPipRenderState> {

    //? if >=26.2 {
    /*public ZoneMapPipRenderer() {
        super();
    }*/
    //?} else {
    public ZoneMapPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    //?}

    @Override
    public Class<ZoneMapPipRenderState> getRenderStateClass() {
        return ZoneMapPipRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "buildcraft_zone_map";
    }

    /** Origin at the centre of the texture so the camera sits in the middle of the viewport. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    //? if >=26.2 {
    /*@Override
    protected void renderToTexture(ZoneMapPipRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector collector) {*/
    //?} else {
    @Override
    protected void renderToTexture(ZoneMapPipRenderState state, PoseStack poseStack) {
    //?}
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RenderType terrainType = BCLibRenderTypes.debugSolid();
        RenderType overlayType = BCLibRenderTypes.debugFilled();
        int wPx = state.x1() - state.x0();
        int hPx = state.y1() - state.y0();
        //? if >=26.2 {
        /*collector.submitCustomGeometry(poseStack, terrainType,
                (pose, vc) -> ZoneMapGeometry.emitTerrain(vc, pose.pose(), state.camera(), wPx, hPx, level));
        collector.submitCustomGeometry(poseStack, overlayType,
                (pose, vc) -> ZoneMapGeometry.emitOverlays(vc, pose.pose(), state.camera(), state.tilePos(),
                        state.layers(), state.bufferLayer(), state.bufferColorIndex(), state.hoverPos(), level));*/
        //?} else {
        Matrix4f mat = poseStack.last().pose();
        ZoneMapGeometry.emitTerrain(this.bufferSource.getBuffer(terrainType), mat, state.camera(), wPx, hPx, level);
        ZoneMapGeometry.emitOverlays(this.bufferSource.getBuffer(overlayType), mat, state.camera(),
                state.tilePos(), state.layers(), state.bufferLayer(), state.bufferColorIndex(),
                state.hoverPos(), level);
        //?}
    }
}
//?}
