/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
//? if <1.21.10 {
/*package buildcraft.core.client.model;

import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

// 1.21.1-only fix for the fragile fluid shard rendering as a translucent, z-fighting blob.
//
// The shard renders through NeoForge's neoforge:fluid_container model (DynamicFluidContainerModel),
// which extrudes the base frame and the (masked, animated, tinted) fluid sprite into thin 3D layers.
// On 1.21.1 it draws the fluid layer with NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT, which is
// NO_CULL + UNSORTED: the extrusion's back/inside faces render and show through the translucent front,
// and all the translucent faces blend in arbitrary (upload) order rather than back-to-front. Together
// that reads as inside-facing z-fighting. 26.1.2 / 1.21.11 (modern render) and 1.12.2 (old render)
// both cull + sort translucent item geometry, so the same shard looks clean there.
//
// Fix: keep the vanilla geometry/mask/animation/tint, but render the model with the CULL + SORTED
// translucent item type 1.21.1 also provides (ITEM_LAYERED_TRANSLUCENT). Culling drops the back/inside
// faces; sorting blends what remains correctly — so water stays see-through but renders as a clean,
// flat, uniform shard like 1.12.2 / 26.1.2 (no opaque fallback needed).
public class FragileFluidShardModel implements IDynamicBakedModel {

    private static final List<RenderType> LAYERED_TRANSLUCENT =
            List.of(NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get());

    private final BakedModel vanilla;
    private final ItemOverrides overrides;

    public FragileFluidShardModel(BakedModel vanilla) {
        this.vanilla = vanilla;
        this.overrides = new ShardOverrides();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
            ModelData data, RenderType renderType) {
        // Unresolved (no stack) — delegate. Real rendering goes through the resolved override below.
        return vanilla.getQuads(state, side, rand, data, renderType);
    }

    @Override public boolean useAmbientOcclusion() { return vanilla.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return vanilla.isGui3d(); }
    @Override public boolean usesBlockLight() { return vanilla.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return vanilla.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return vanilla.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return overrides; }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return LAYERED_TRANSLUCENT;
    }

    // Resolves the vanilla per-fluid model (keeping mask/animation/tint), then wraps it to force the
    // cull + sorted translucent render type.
    private final class ShardOverrides extends ItemOverrides {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level,
                LivingEntity entity, int seed) {
            BakedModel resolved = vanilla.getOverrides().resolve(vanilla, stack, level, entity, seed);
            if (resolved == null) {
                resolved = vanilla;
            }
            return new WrappedModel(resolved);
        }
    }

    // Serves the resolved vanilla quads unchanged, but on the cull + sorted translucent sheet (the fix).
    // Everything else delegates to the resolved model.
    private final class WrappedModel implements IDynamicBakedModel {
        private final BakedModel resolved;

        WrappedModel(BakedModel resolved) {
            this.resolved = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                ModelData data, RenderType renderType) {
            return resolved.getQuads(state, side, rand, data, renderType);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return LAYERED_TRANSLUCENT;
        }

        @Override public boolean useAmbientOcclusion() { return resolved.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return resolved.isGui3d(); }
        @Override public boolean usesBlockLight() { return resolved.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return resolved.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return resolved.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}*/
//?}
