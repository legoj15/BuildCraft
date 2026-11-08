/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

/**
 * What BC's quad and model code needs from the loader's model API: the quad data the loader adds to vanilla's, and
 * (1.21.1) the loader-typed baked models BC's item and shard models hand back.
 */
public interface ClientModels {
    //? if >=1.21.11 {
    /**
     * {@code quad} carrying one colour per vertex (0xAARRGGBB, multiplied into the vertex colour when drawn). NeoForge
     * adds per-vertex colours to vanilla's {@code BakedQuad}; Fabric API 26.3 does not, so there the quad is returned
     * as is and its colours are kept beside it, readable through {@link #vertexColour}. A draw that hands the bare quad
     * to vanilla shows it untinted on Fabric, so BC's own draws re-apply the colours there: its pipe block model (the
     * chunk mesh) and {@link #setItemQuads} (item models).
     */
    BakedQuad withVertexColours(BakedQuad quad, int argb0, int argb1, int argb2, int argb3);

    /**
     * The colour (0xAARRGGBB) {@code quad} carries for {@code vertex} (0-3) as {@link #withVertexColours} gave it, or
     * opaque white (-1) where it carries none. This is the colour a draw that goes through the quad's own sink
     * (NeoForge's {@code putBakedQuad}) multiplies into the vertex colour; a draw that rebuilds vertices itself (the
     * snapshot preview's {@code MutableQuad} path) reads it here to do the same.
     */
    int vertexColour(BakedQuad quad, int vertex);
    //?}

    //? if >=26.3 {
    /**
     * A quad's material: vanilla's eight components ({@code shadeOverride} null to light by the quad's own face).
     * NeoForge adds an ambient-occlusion flag and deprecates the constructor without it; BC's quads always take
     * ambient occlusion, which is what that constructor passes.
     */
    BakedQuad.MaterialInfo materialInfo(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            net.minecraft.client.renderer.chunk.ChunkSectionLayer layer,
            net.minecraft.client.renderer.rendertype.RenderType itemSheet,
            net.minecraft.client.renderer.rendertype.RenderType glintSheet,
            net.minecraft.client.renderer.rendertype.RenderType glintSpecialSheet, int tintIndex,
            net.minecraft.core.Direction shadeOverride, int lightEmission);
    //?} elif >=26.1 {
    /*/^*
     * A quad's material on 26.1 and 26.2: vanilla's six components. NeoForge adds an ambient-occlusion flag (in its
     * patch since 26.1-pre3); BC's quads always take ambient occlusion.
     ^/
    BakedQuad.MaterialInfo materialInfo(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            net.minecraft.client.renderer.chunk.ChunkSectionLayer layer,
            net.minecraft.client.renderer.rendertype.RenderType itemSheet, int tintIndex, boolean shade,
            int lightEmission);
    *///?}

    //? if >=1.21.11 {
    /**
     * Gives an item model's layer {@code quads}, each drawn with the per-vertex colours {@link #withVertexColours} gave
     * it (from 26.3 a layer takes one {@code ItemQuads}, split into its solid and translucent submits; before, its
     * quad list). NeoForge: the quads themselves carry the colours. Fabric: the uncoloured quads go that way and the
     * coloured ones through the layer's mesh emitter ({@code FabricLayerRenderState#emitter}), the one item path in
     * Fabric API that takes a colour per vertex. (1.21.10 keeps a quad's colours in its own vertex data, which every
     * draw reads, so it needs none of this.)
     */
    void setItemQuads(net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer,
            java.util.List<BakedQuad> quads);
    //?}

    //? if <1.21.10 {
    /*/^*
     * A leaf item model serving {@code quads} (on the general, null-side pass only) on one {@code renderType} sheet:
     * what 1.21.1's dynamic BC item models return per display context, with the transform baked into the quads.
     *^/
    net.minecraft.client.resources.model.BakedModel fixedQuadItemModel(java.util.List<BakedQuad> quads,
            net.minecraft.client.renderer.texture.TextureAtlasSprite particle, boolean gui3d,
            net.minecraft.client.renderer.rendertype.RenderType renderType);

    /^*
     * The fragile fluid shard's 1.21.1 model fix around the loader's fluid-container model {@code vanilla}: drops the
     * fluid layer's (tint index 1) extrusion side walls and renders on the cull-and-sorted translucent item sheet.
     *^/
    net.minecraft.client.resources.model.BakedModel layeredFluidContainerModel(
            net.minecraft.client.resources.model.BakedModel vanilla);
    *///?}
}
