/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
//? if <1.21.10 {
/*package buildcraft.core.client.model;

import java.util.ArrayList;
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
// which EXTRUDES the base frame and the (masked, animated, tinted) fluid sprite into thin 3D shapes:
// a flat front face, a flat back face, and per-pixel SIDE WALLS along the sprite's alpha edges. On
// 1.21.1's classic item render those translucent side walls (plus the fluid layer's NO_CULL +
// UNSORTED render type) overlap and blend in arbitrary order — the inside-facing z-fighting.
// 26.1.2 / 1.21.11 (modern render) and 1.12.2 (old render) render it cleanly.
//
// Fix (two parts), keeping the vanilla geometry/mask/animation/tint:
//   1. Tint-aware side-wall filter. The BASE FRAME (tintIndex 0) keeps its full extrusion — its outer
//      silhouette side walls give the shard real 3D depth (the white frame pixels render as cuboids,
//      so it reads as a fluid container, not flat planes). The FLUID layer (tintIndex 1) keeps only
//      its flat front/back faces (drop its side walls), so it sits inside the frame as a clean fluid
//      surface instead of a second extruded shell whose per-pixel walls intersect the frame's (the
//      "collection of intersecting voxels" z-fighting). A single-layer extrusion only walls its outer
//      boundary, so keeping the frame's walls = the item's outermost edges only.
//   2. Render with the CULL + SORTED translucent item type 1.21.1 provides (ITEM_LAYERED_TRANSLUCENT),
//      not the NO_CULL + UNSORTED one the fluid_container picks, so the frame and the fluid inside it
//      blend correctly. Water stays see-through; the shard reads like 1.12.2 / 26.1.2.
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

    // Serves the resolved vanilla quads with the extrusion side walls removed (keep only the flat
    // front/back faces) and on the cull + sorted translucent sheet. Everything else delegates.
    private final class WrappedModel implements IDynamicBakedModel {
        private final BakedModel resolved;

        WrappedModel(BakedModel resolved) {
            this.resolved = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                ModelData data, RenderType renderType) {
            List<BakedQuad> in = resolved.getQuads(state, side, rand, data, renderType);
            if (in.isEmpty()) {
                return in;
            }
            // Frame (tintIndex != 1): keep the full extrusion (front/back + outer silhouette walls)
            // for real 3D depth. Fluid (tintIndex 1): keep only the flat front/back faces (normal
            // along Z), dropping its side walls so it doesn't form a second voxel shell that
            // intersects the frame and z-fights.
            List<BakedQuad> out = new ArrayList<>(in.size());
            for (BakedQuad q : in) {
                if (q.getTintIndex() != 1 || q.getDirection().getAxis() == Direction.Axis.Z) {
                    out.add(q);
                }
            }
            return out;
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
