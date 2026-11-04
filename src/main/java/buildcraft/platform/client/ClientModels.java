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
     * uncoloured (BC's quads then draw untinted white where they relied on vertex colour).
     */
    BakedQuad withVertexColours(BakedQuad quad, int argb0, int argb1, int argb2, int argb3);
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
