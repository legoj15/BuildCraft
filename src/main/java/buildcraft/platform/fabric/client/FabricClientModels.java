/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import net.minecraft.client.resources.model.geometry.BakedQuad;

import buildcraft.platform.client.ClientModels;

/^* {@link ClientModels} on Fabric: vanilla's quads, which carry no loader additions. ^/
public final class FabricClientModels implements ClientModels {
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
        // Gap: vanilla 26.3's BakedQuad has no per-vertex colour and Fabric API adds none to it (only its mesh
        // QuadEmitter#color does, which a BakedQuad cannot carry), so the quad stays uncoloured.
        return quad;
    }
}
*///?}
