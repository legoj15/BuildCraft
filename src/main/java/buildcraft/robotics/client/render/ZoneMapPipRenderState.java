/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

// Whole-file >=1.21.10: PictureInPictureRenderState is the 1.21.5+ GUI PiP pipeline, absent on 1.21.1
// (where the Zone Planner keeps its non-rendered placeholder). Mirrors BlueprintPipRenderState.
//? if >=1.21.10 {
import javax.annotation.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
//? if >=26.1 {
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//?} else {
/*import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;*/
//?}

import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.zone.ZonePlan;

/**
 * PiP state for the Zone Planner's 3D map viewport, rendered into an offscreen texture by
 * {@link ZoneMapPipRenderer} and blitted into the GUI. Carries everything the renderer needs that the
 * GUI samples fresh each frame: the camera (pan/zoom), the planner's position (zones are stored
 * tile-relative), the 16 dye layers, an optional in-progress paint preview, and the hovered column.
 *
 * <p>{@link #scale()} returns the camera's pixels-per-block so the base class's pose scale converts the
 * renderer's block-unit canvas coordinates straight to texture pixels. {@code bounds} is the
 * scissor-clipped blit region (see {@link PictureInPictureRenderState#getBounds}).
 */
public record ZoneMapPipRenderState(
    int x0,
    int y0,
    int x1,
    int y1,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    BlockPos tilePos,
    ZoneMapCamera camera,
    ZonePlan[] layers,
    @Nullable ZonePlan bufferLayer,
    int bufferColorIndex,
    @Nullable BlockPos hoverPos
) implements PictureInPictureRenderState {

    public ZoneMapPipRenderState(int x0, int y0, int x1, int y1,
                                 @Nullable ScreenRectangle scissorArea,
                                 BlockPos tilePos, ZoneMapCamera camera, ZonePlan[] layers,
                                 @Nullable ZonePlan bufferLayer, int bufferColorIndex,
                                 @Nullable BlockPos hoverPos) {
        this(x0, y0, x1, y1, scissorArea,
             PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea),
             tilePos, camera, layers, bufferLayer, bufferColorIndex, hoverPos);
    }

    @Override
    public float scale() {
        return (float) camera.pxPerBlock;
    }
}
//?}
