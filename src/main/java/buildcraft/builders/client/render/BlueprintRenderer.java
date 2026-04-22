/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import buildcraft.builders.client.render.pip.BlueprintPipRenderState;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.Snapshot;

/**
 * Thin adapter from the {@link buildcraft.builders.client.tooltip.BlueprintTooltipOverlay
 * BlueprintTooltipOverlay} call-site to the PiP pipeline. Builds a
 * {@link BlueprintPipRenderState} sized to the requested viewport and hands it off to
 * {@link GuiGraphicsExtractor#submitPictureInPictureRenderState}. The actual 3D rendering,
 * including rotation animation and depth-buffer occlusion, happens in
 * {@link buildcraft.builders.client.render.pip.BlueprintPipRenderer}.
 * <p>
 * This replaced an earlier 2D implementation that drew a stack of axis-aligned item sprites per
 * cell; the sprites' positions rotated but their orientation didn't, so the preview looked like a
 * wonky lattice shearing around rather than a cohesive rotating model.
 */
public class BlueprintRenderer {

    private static final Logger LOGGER = LogManager.getLogger("BCBlueprintRenderer");

    /**
     * Snapshots whose rejection (not a Blueprint) has already been logged, to prevent the
     * re-render-every-frame tooltip from spamming the log. Identity-hashed because Snapshot is
     * a mutable record-ish struct, not a value-equality type.
     */
    private static final Set<Integer> LOGGED_REJECTIONS =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Safety margin on top of the structure's 3D diagonal. The diagonal already captures the
     * worst-case projected extent for any combination of pitch and yaw (a structure rotated so
     * its space diagonal aligns with the screen normal projects at most {@code diagonal} across
     * in any screen-space direction). The envelope just adds a small slack so antialiased edges
     * don't clip the PiP texture border.
     * <p>
     * Replaces an earlier max-dimension + sqrt(2) approach that only accounted for 2D rotation
     * and let single-block or near-cube previews escape the viewport corners as they spun.
     */
    private static final float FIT_ENVELOPE = 1.05f;

    public static void renderSnapshot(GuiGraphicsExtractor graphics, Snapshot snapshot,
                                      int viewportX, int viewportY,
                                      int viewportWidth, int viewportHeight) {
        if (!(snapshot instanceof Blueprint blueprint)) {
            if (LOGGED_REJECTIONS.add(System.identityHashCode(snapshot))) {
                // Templates (BitSet-only, no palette) go through here too; they have no
                // per-block model to render, so the preview is empty — matches the 1.12.2
                // behavior. If we add a template preview later, broaden the PiP state rather
                // than branching here.
                LOGGER.info("renderSnapshot skipped: not a Blueprint (class={}) size={}",
                        snapshot.getClass().getSimpleName(), snapshot.size);
            }
            return;
        }

        int sizeX = Math.max(1, snapshot.size.getX());
        int sizeY = Math.max(1, snapshot.size.getY());
        int sizeZ = Math.max(1, snapshot.size.getZ());
        float diagonal = (float) Math.sqrt((double) sizeX * sizeX + (double) sizeY * sizeY + (double) sizeZ * sizeZ);

        // The base PiP class multiplies the scale by guiScale when it sets up the inner pose
        // (see PictureInPictureRenderer#prepare line: `float scale = guiScale * renderState.scale()`).
        // The offscreen texture itself is `(x1-x0)*guiScale` pixels wide. So:
        //     pixels_per_world_unit = guiScale * renderState.scale()
        //     texture_width_in_world_units = textureWidthPx / pixels_per_world_unit
        //                                  = ((x1-x0)*guiScale) / (guiScale * scale)
        //                                  = (x1-x0) / scale
        // We want that to equal diagonal*FIT_ENVELOPE, so:
        //     scale = viewport / (diagonal * FIT_ENVELOPE)
        // The guiScale factor cancels — we don't need to know or query it here. Using the 3D
        // diagonal (rather than max(x,y,z)) keeps the structure inside the viewport at every
        // yaw/pitch, including the corner-on angles where a near-cubic box like the Architect
        // Table's 1×1×1 single-block live preview previously poked out of the PiP texture.
        float viewportSpan = Math.min(viewportWidth, viewportHeight);
        float scale = viewportSpan / (diagonal * FIT_ENVELOPE);

        BlueprintPipRenderState state = new BlueprintPipRenderState(
                blueprint,
                viewportX,
                viewportY,
                viewportX + viewportWidth,
                viewportY + viewportHeight,
                scale,
                // Pass the current scissor rectangle so the blit respects tooltip clipping. The
                // PiP base class intersects this with the full bounds internally.
                graphics.peekScissorStack());
        graphics.submitPictureInPictureRenderState(state);
    }
}
