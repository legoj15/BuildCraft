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
import net.minecraft.client.renderer.Sheets;
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

import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

// 1.21.1-only fix for the fragile fluid shard rendering as a translucent, z-fighting blob.
//
// The shard renders through NeoForge's neoforge:fluid_container model (DynamicFluidContainerModel),
// which extrudes the base frame and the (masked, animated, tinted) fluid sprite into thin 3D layers
// and draws the fluid layer TRANSLUCENT. On 26.1.2 / 1.21.11's modern item render that looks clean,
// but on 1.21.1's classic item render the translucent front lets you see through to the extrusion's
// back/inside faces, which read as inside-facing z-fighting (the "odd blob").
//
// Fix: keep the vanilla geometry/mask/animation/tint, but render the whole model on the OPAQUE cutout
// sheet. Opaque draws only the frontmost surface (depth-test + draw order resolve the coplanar layers
// exactly like a normal multi-layer item, e.g. a spawn egg), so there is nothing to see through and
// nothing to z-fight — a flat, uniform shard like 1.12.2 / 26.1.2. The fluid still animates and the
// frame stays neutral; it just no longer reads as a see-through volume.
public class FragileFluidShardModel implements IDynamicBakedModel {

    private static final List<RenderType> CUTOUT = List.of(Sheets.cutoutBlockSheet());

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
        return CUTOUT;
    }

    // Resolves the vanilla per-fluid model (keeping mask/animation/tint), then wraps it to force the
    // opaque cutout render type.
    private final class ShardOverrides extends ItemOverrides {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level,
                LivingEntity entity, int seed) {
            BakedModel resolved = vanilla.getOverrides().resolve(vanilla, stack, level, entity, seed);
            if (resolved == null) {
                resolved = vanilla;
            }
            return new CutoutModel(resolved);
        }
    }

    // Serves the resolved vanilla quads unchanged, but on the opaque cutout sheet (the fix). Everything
    // else delegates to the resolved model.
    private final class CutoutModel implements IDynamicBakedModel {
        private final BakedModel resolved;

        CutoutModel(BakedModel resolved) {
            this.resolved = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                ModelData data, RenderType renderType) {
            return resolved.getQuads(state, side, rand, data, renderType);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return CUTOUT;
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
