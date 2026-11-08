/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

import buildcraft.platform.client.ClientModels;

/^*
 * {@link ClientModels} on Fabric: vanilla's quads, which carry no loader additions, so the per-vertex colours BC bakes
 * into its quads live in a side table keyed by quad identity (see {@link #VERTEX_COLOURS}), and BC's own draws put them
 * back on through a Fabric {@link QuadEmitter} ({@link #applyVertexColours}): the pipe block model and {@link #setItemQuads}.
 ^/
public final class FabricClientModels implements ClientModels {
    /^*
     * Per-vertex colours (0xAARRGGBB x4) of the quads {@link #withVertexColours} was given, for the draws that rebuild
     * vertices themselves (the snapshot preview) or emit them through a {@code QuadEmitter} (the pipe block model, item
     * layers) and so can multiply them in. Vanilla 26.3's {@code BakedQuad} has no
     * colour slot and Fabric API's only colour setter is the mesh {@code QuadEmitter}, which a {@code BakedQuad}
     * cannot carry, so the colours ride beside the quad instead. Weak identity keys (a record's {@code equals} is by
     * value, so equal quads would share one entry): an entry lives exactly as long as the quad does, so a model
     * reload that drops the baked lists drops the colours with them. Concurrent: chunk sections compile on several
     * threads at once.
     ^/
    private static final ConcurrentMap<BakedQuad, int[]> VERTEX_COLOURS = new MapMaker().weakKeys().makeMap();

    //? if >=26.3 {
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
    //?} elif >=26.1 {
    /^@Override
    public BakedQuad.MaterialInfo materialInfo(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            net.minecraft.client.renderer.chunk.ChunkSectionLayer layer,
            net.minecraft.client.renderer.rendertype.RenderType itemSheet, int tintIndex, boolean shade,
            int lightEmission) {
        return new BakedQuad.MaterialInfo(sprite, layer, itemSheet, tintIndex, shade, lightEmission);
    }
    ^///?}

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

    @Override
    public void setItemQuads(ItemStackRenderState.LayerRenderState layer, List<BakedQuad> quads) {
        List<BakedQuad> plain = new ArrayList<>(quads.size());
        QuadEmitter emitter = null;
        for (BakedQuad quad : quads) {
            if (!VERTEX_COLOURS.containsKey(quad)) {
                plain.add(quad);
                continue;
            }
            if (emitter == null) {
                // Fabric API's mesh on the layer, drawn in the same item submit as the layer's quads.
                emitter = layer.emitter();
            }
            emitter.fromBakedQuad(quad);
            applyVertexColours(emitter, quad);
            emitter.emit();
        }
        //? if >=26.3 {
        layer.setQuads(net.minecraft.client.resources.model.geometry.ItemQuads.split(plain));
        //?} else {
        /^layer.prepareQuadList().addAll(plain);
        ^///?}
    }

    /^*
     * Gives the quad {@code emitter} holds (just filled by {@code fromBakedQuad(quad)}, which leaves the colour white)
     * the per-vertex colours {@link #withVertexColours} gave {@code quad}, if any. ARGB on both sides, so the colours go
     * across unchanged.
     ^/
    public static void applyVertexColours(QuadEmitter emitter, BakedQuad quad) {
        int[] colours = VERTEX_COLOURS.get(quad);
        if (colours != null) {
            emitter.color(colours[0], colours[1], colours[2], colours[3]);
        }
    }
}
*///?}
