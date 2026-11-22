/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && <1.21.10 {
/*import java.util.List;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/^*
 * A leaf item model over a fixed list of baked quads, for 1.21.1's classic item render
 * ({@code ClientModels#fixedQuadItemModel}): BuildCraft's dynamic item models (facades, gates, lenses, pipes) resolve
 * per stack and hand one of these back per display context, with whatever transform they did not bake already applied
 * to the pose. One render type covers every quad. The Forge 52 twin of NeoForge 1.21.1's {@code NeoForgeQuadItemModel}.
 *^/
final class ForgeQuadItemModel implements IDynamicBakedModel {
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final boolean gui3d;
    private final List<RenderType> renderTypes;

    ForgeQuadItemModel(List<BakedQuad> quads, TextureAtlasSprite particle, boolean gui3d, RenderType renderType) {
        this.quads = quads;
        this.particle = particle;
        this.gui3d = gui3d;
        this.renderTypes = List.of(renderType);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
            RenderType renderType) {
        // Item quads are drawn on the general (null-side) pass only.
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

    // Abstract and deprecated on Forge 52 (its model-data overload falls back to it), so it must be overridden.
    @Override
    @SuppressWarnings("deprecation")
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
*///?}
