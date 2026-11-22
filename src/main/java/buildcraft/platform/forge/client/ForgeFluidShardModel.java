/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && <1.21.10 {
/*import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.client.ForgeRenderTypes;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/^*
 * The fragile fluid shard's 1.21.1 item model ({@code ClientModels#layeredFluidContainerModel}), the Forge 52 twin of
 * NeoForge 1.21.1's {@code NeoForgeFluidShardModel}. The shard's fluid-container model extrudes the frame and the masked
 * fluid sprite into thin shapes whose translucent per-pixel side walls z-fight on 1.21.1's classic item render. The
 * per-stack model is resolved as before ({@code ForgeFluidContainerGeometry}), then served with the fluid layer's side
 * walls dropped (tint index 1 keeps only its front and back faces; the frame keeps its full extrusion for depth) and on
 * the culled, sorted translucent item type, so the frame and the fluid blend in order.
 *^/
final class ForgeFluidShardModel implements IDynamicBakedModel {
    private static final List<RenderType> LAYERED_TRANSLUCENT = List.of(ForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get());

    private final BakedModel vanilla;
    private final ItemOverrides overrides = new ShardOverrides();

    ForgeFluidShardModel(BakedModel vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
            RenderType renderType) {
        // Unresolved (no stack): the wrapped model's quads. A stack draws through the resolved override below.
        return vanilla.getQuads(state, side, rand, data, renderType);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return vanilla.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return vanilla.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return vanilla.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    // Abstract and deprecated on Forge 52 (its model-data overload falls back to it), so it must be overridden.
    @Override
    @SuppressWarnings("deprecation")
    public TextureAtlasSprite getParticleIcon() {
        return vanilla.getParticleIcon(ModelData.EMPTY);
    }

    // Forwarded on purpose: the item entity's ground bob and the default applyTransform both read it.
    @Override
    @SuppressWarnings("deprecation")
    public ItemTransforms getTransforms() {
        return vanilla.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return LAYERED_TRANSLUCENT;
    }

    // Resolves the wrapped per-fluid model (mask, animation and tint kept), then serves it filtered.
    private final class ShardOverrides extends ItemOverrides {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
            BakedModel resolved = vanilla.getOverrides().resolve(vanilla, stack, level, entity, seed);
            return new Resolved(resolved == null ? vanilla : resolved);
        }
    }

    private static final class Resolved implements IDynamicBakedModel {
        private final BakedModel resolved;

        Resolved(BakedModel resolved) {
            this.resolved = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                RenderType renderType) {
            List<BakedQuad> in = resolved.getQuads(state, side, rand, data, renderType);
            if (in.isEmpty()) {
                return in;
            }
            // The frame (any tint index but 1) keeps its whole extrusion; the fluid keeps its flat faces only.
            List<BakedQuad> out = new ArrayList<>(in.size());
            for (BakedQuad quad : in) {
                if (quad.getTintIndex() != 1 || quad.getDirection().getAxis() == Direction.Axis.Z) {
                    out.add(quad);
                }
            }
            return out;
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return LAYERED_TRANSLUCENT;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return resolved.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return resolved.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return resolved.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public TextureAtlasSprite getParticleIcon() {
            return resolved.getParticleIcon(ModelData.EMPTY);
        }

        @Override
        @SuppressWarnings("deprecation")
        public ItemTransforms getTransforms() {
            return resolved.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
*///?}
