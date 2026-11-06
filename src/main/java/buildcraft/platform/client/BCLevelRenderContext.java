/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.phys.Vec3;

/**
 * One frame of BuildCraft's world-space overlay pass ({@link ClientHooks#onLevelRender}): the point after the
 * translucent blocks where markers, lasers, quarry frames and the like are drawn.
 *
 * <p>From 26.1 the geometry is queued on the frame's {@link #collector() submit collector}; before 26.1 it is
 * drawn immediately into {@link #bufferSource() the main buffer source}, which the caller flushes itself.
 */
public interface BCLevelRenderContext {

    /** The level pose stack, at the camera's rotation and with no translation (draw relative to the camera). */
    PoseStack poseStack();

    /** The camera's world position. */
    Vec3 cameraPos();

    //? if >=26.1 {
    /** The retained-mode collector the frame's custom geometry is submitted to. */
    net.minecraft.client.renderer.SubmitNodeCollector collector();
    //?} else {
    /*/^* The main immediate-mode buffer source. *^/
    net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource();*/
    //?}
}
