/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && >=1.21.10 {
/*import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//? if >=26.3 {
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
//?}
import net.minecraft.util.ARGB;

import buildcraft.platform.client.ClientModels;

/^*
 * {@link ClientModels} on MinecraftForge (not NeoForge). Forge 26.3's {@code BakedQuad} is vanilla's, with no colour slot
 * (NeoForge adds {@code BakedColors}), so the per-vertex colours BuildCraft bakes into its quads live in a side table
 * keyed by quad identity, as on Fabric ({@link #VERTEX_COLOURS}); where Fabric re-emits a coloured quad through its mesh
 * API, the Forge node multiplies the colours into vanilla's per-quad {@code QuadInstance} at the two places a quad is
 * drawn ({@code ModelBlockRendererMixin} for block models and the chunk mesh, {@code ItemFeatureRendererMixin} for item
 * layers), so the quad lists themselves stay plain.
 *^/
public final class ForgeClientModels implements ClientModels {
    /^*
     * Per-vertex colours (0xAARRGGBB x4) of the quads {@link #withVertexColours} was given. Weak identity keys (a record's
     * {@code equals} is by value, so equal quads would share one entry): an entry lives exactly as long as its quad, so a
     * model reload that drops the baked lists drops the colours with them. Concurrent: chunk sections compile on several
     * threads at once.
     *^/
    private static final ConcurrentMap<BakedQuad, int[]> VERTEX_COLOURS = new MapMaker().weakKeys().makeMap();

    //? if >=26.3 {
    @Override
    public BakedQuad.MaterialInfo materialInfo(TextureAtlasSprite sprite, ChunkSectionLayer layer, RenderType itemSheet,
            RenderType glintSheet, RenderType glintSpecialSheet, int tintIndex, Direction shadeOverride,
            int lightEmission) {
        // Vanilla's constructor: Forge adds nothing to the material.
        return new BakedQuad.MaterialInfo(sprite, layer, itemSheet, glintSheet, glintSpecialSheet, tintIndex,
                shadeOverride, lightEmission);
    }
    //?} else {
    /^@Override
    public BakedQuad.MaterialInfo materialInfo(TextureAtlasSprite sprite, ChunkSectionLayer layer, RenderType itemSheet,
            int tintIndex, boolean shade, int lightEmission) {
        // Vanilla's six-component constructor (26.1, 26.2): Forge adds nothing to the material.
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
        // The colours go on when the quads are drawn (ItemFeatureRendererMixin).
        //? if >=26.3 {
        layer.setQuads(ItemQuads.split(quads));
        //?} else {
        /^layer.prepareQuadList().addAll(quads);
        ^///?}
    }

    /^*
     * Multiplies the per-vertex colours {@link #withVertexColours} gave {@code quad}, if any, into {@code instance}'s
     * (which already carry the light and tint), for the draw mixins.
     *^/
    public static void applyVertexColours(BakedQuad quad, QuadInstance instance) {
        int[] colours = VERTEX_COLOURS.get(quad);
        if (colours != null) {
            for (int vertex = 0; vertex < 4; vertex++) {
                instance.setColor(vertex, ARGB.multiply(instance.getColor(vertex), colours[vertex]));
            }
        }
    }
}
*///?}
