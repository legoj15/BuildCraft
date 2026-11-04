/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

//? if >=1.21.11 {
import net.neoforged.neoforge.client.model.quad.BakedColors;
//?}

import buildcraft.platform.client.ClientModels;

/**
 * {@link ClientModels} on NeoForge: its per-vertex {@code BakedColors} on the patched {@link BakedQuad}, and the
 * 1.21.1 {@code IDynamicBakedModel}s.
 */
public final class NeoForgeClientModels implements ClientModels {
    //? if >=26.1 {
    @Override
    public BakedQuad withVertexColours(BakedQuad quad, int argb0, int argb1, int argb2, int argb3) {
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(),
                quad.materialInfo(), quad.bakedNormals(), BakedColors.of(argb0, argb1, argb2, argb3));
    }
    //?} elif >=1.21.11 {
    /*@Override
    public BakedQuad withVertexColours(BakedQuad quad, int argb0, int argb1, int argb2, int argb3) {
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.tintIndex(),
                quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission(), quad.bakedNormals(),
                BakedColors.of(argb0, argb1, argb2, argb3), quad.hasAmbientOcclusion());
    }*/
    //?}

    //? if <1.21.10 {
    /*@Override
    public net.minecraft.client.resources.model.BakedModel fixedQuadItemModel(java.util.List<BakedQuad> quads,
            net.minecraft.client.renderer.texture.TextureAtlasSprite particle, boolean gui3d,
            net.minecraft.client.renderer.rendertype.RenderType renderType) {
        return new NeoForgeQuadItemModel(quads, particle, gui3d, renderType);
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel layeredFluidContainerModel(
            net.minecraft.client.resources.model.BakedModel vanilla) {
        return new NeoForgeFluidShardModel(vanilla);
    }
    *///?}
}
//?}
