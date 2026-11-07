/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import buildcraft.platform.client.ClientModels;

/^*
 * {@link ClientModels} on Fabric: vanilla's quads, which carry no loader additions, so the per-vertex colours BC bakes
 * into its quads live in a side table keyed by quad identity (see {@link #VERTEX_COLOURS}).
 ^/
public final class FabricClientModels implements ClientModels {
    /^*
     * Per-vertex colours (0xAARRGGBB x4) of the quads {@link #withVertexColours} was given, for the draws that rebuild
     * vertices themselves (the snapshot preview) and can multiply them in. Vanilla 26.3's {@code BakedQuad} has no
     * colour slot and Fabric API's only colour setter is the mesh {@code QuadEmitter}, which a {@code BakedQuad}
     * cannot carry, so the colours ride beside the quad instead. Weak identity keys (a record's {@code equals} is by
     * value, so equal quads would share one entry): an entry lives exactly as long as the quad does, so a model
     * reload that drops the baked lists drops the colours with them. Concurrent: chunk sections compile on several
     * threads at once.
     ^/
    private static final ConcurrentMap<BakedQuad, int[]> VERTEX_COLOURS = new MapMaker().weakKeys().makeMap();

    @Override
    public BakedQuad.MaterialInfo materialInfo(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            net.minecraft.client.renderer.chunk.ChunkSectionLayer layer,
            net.minecraft.client.renderer.rendertype.RenderType itemSheet,
            net.minecraft.client.renderer.rendertype.RenderType glintSheet,
            net.minecraft.client.renderer.rendertype.RenderType glintSpecialSheet, int tintIndex,
            net.minecraft.core.Direction shadeOverride, int lightEmission) {
        // Vanilla's constructor: there is no ambient-occlusion flag to set (vanilla decides it per model).
        return new BakedQuad.MaterialInfo(sprite, layer, itemSheet, glintSheet, glintSpecialSheet, tintIndex,
                shadeOverride, lightEmission);
    }

    @Override
    public BakedQuad withVertexColours(BakedQuad quad, int argb0, int argb1, int argb2, int argb3) {
        // The quad itself stays vanilla's. An all-white quad is the default, so it is not worth an entry.
        if (argb0 != -1 || argb1 != -1 || argb2 != -1 || argb3 != -1) {
            VERTEX_COLOURS.put(quad, new int[] {argb0, argb1, argb2, argb3});
        }
        return quad;
    }

    @Override
    public int vertexColour(BakedQuad quad, int vertex) {
        int[] colours = VERTEX_COLOURS.get(quad);
        return colours == null ? -1 : colours[vertex];
    }
}
*///?}
