/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import java.util.List;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

import buildcraft.platform.render.PipeRenderData;

/**
 * The quads of a pipe holder's block model, generated from the block entity's {@link PipeRenderData}: what the
 * loader's block model registered through {@link ClientRegistrationSinks#pipeBlockModel} draws in the chunk mesh.
 * Called while a chunk section meshes, possibly off the main thread. Every quad is unculled (sub-block geometry).
 */
public interface PipeModelQuads {
    /** False when {@code data} has nothing to draw yet: the model then draws the block's own baked model instead. */
    boolean hasGeometry(PipeRenderData data);

    /**
     * The chunk-mesh geometry key for {@code data} (only asked when {@link #hasGeometry} is true): equal keys mean
     * equal quads, so a section re-meshes only when the key changes.
     */
    Object geometryKey(PipeRenderData data);

    /** The cutout-layer quads (the pipe body and its pluggables). */
    List<BakedQuad> cutoutQuads(PipeRenderData data);

    /** The translucent-layer quads (a painted pipe's colour overlay). */
    List<BakedQuad> translucentQuads(PipeRenderData data);
}
