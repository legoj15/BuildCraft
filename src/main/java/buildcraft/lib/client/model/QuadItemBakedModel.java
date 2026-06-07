/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.model;

// QuadItemBakedModel is a 1.21.1-only leaf model — see the gated block below.
// On >=1.21.10 nodes the modern ItemModel / ItemStackRenderState paradigm replaces it
// (dynamic item models implement ItemModel.update there), so this compilation unit is
// intentionally empty on those nodes. The block is commented out in the source tree
// because the active Stonecutter node (26.1.2) does not match <1.21.10; only line
// comments appear inside so no "*/" prematurely closes the deactivated block.
//? if <1.21.10 {
/*import java.util.List;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

// Leaf BakedModel wrapping a fixed list of pre-baked item quads for 1.21.1's classic
// item-render path (ItemRenderer -> getRenderPasses -> getRenderTypes -> getQuads).
//
// Dynamic BuildCraft item models (facade / gate / lens / pipe) resolve per-stack via
// their ItemOverrides, then return one of these per ItemDisplayContext from
// applyTransform(...). The per-context display transform is baked directly into the
// quads (so getTransforms() is NO_TRANSFORMS here), and one RenderType sheet covers all
// quads: cutoutBlockSheet() for opaque geometry, translucentItemSheet() when a coloured
// translucent overlay is present (alpha-255 cutout quads stay opaque on that sheet).
public final class QuadItemBakedModel implements BakedModel {
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final boolean gui3d;
    private final RenderType renderType;

    public QuadItemBakedModel(List<BakedQuad> quads, TextureAtlasSprite particle, boolean gui3d, RenderType renderType) {
        this.quads = quads;
        this.particle = particle;
        this.gui3d = gui3d;
        this.renderType = renderType;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        // Item quads are emitted only on the general (null-side) pass.
        return side == null ? quads : List.of();
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(renderType);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
*///?}
