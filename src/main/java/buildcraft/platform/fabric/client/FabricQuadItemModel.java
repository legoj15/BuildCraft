/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <1.21.10 {
/*import java.util.List;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClassicItemRenderType;

/^*
 * {@code ClientModels#fixedQuadItemModel} on Fabric 1.21.1, the twin of {@code NeoForgeQuadItemModel}: a fixed list of
 * item quads (on the general, null-side pass only), whose producer has already baked or posed its display transform, drawn on one sheet, which
 * {@code ItemRendererMixin} reads through {@link ClassicItemRenderType}.
 *^/
final class FabricQuadItemModel implements ClassicItemRenderType {
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final boolean gui3d;
    private final List<RenderType> renderTypes;

    FabricQuadItemModel(List<BakedQuad> quads, TextureAtlasSprite particle, boolean gui3d, RenderType renderType) {
        this.quads = quads;
        this.particle = particle;
        this.gui3d = gui3d;
        this.renderTypes = List.of(renderType);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return side == null ? quads : List.of();
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return renderTypes;
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

    // The producer has applied the transform (in the quads or on the pose); NeoForge's model reports none either.
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
