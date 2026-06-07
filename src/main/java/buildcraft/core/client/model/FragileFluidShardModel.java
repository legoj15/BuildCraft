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

import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

// 1.21.1-only fix for the fragile fluid shard's base/fluid z-fight.
//
// The shard renders through NeoForge's neoforge:fluid_container model (DynamicFluidContainerModel),
// which lays the fluid layer (tintIndex 1 — the masked + animated + tinted fluid sprite) a mere
// FLUID_TRANSFORM (~0.001) in front of the base frame layer (tintIndex 0). On 26.1.2 / 1.21.11 that
// gap is enough; on 1.21.1's classic item render it is below the depth-buffer precision, so the two
// layers z-fight into collapsed-looking noise once they stop sharing a colour (after the frame-tint fix).
//
// Rather than reimplement the masked-fluid extrusion, this WRAPS the vanilla baked shard model: it
// resolves the per-fluid model exactly as vanilla does (keeping the animation/mask/tint), then nudges
// only the fluid-layer quads (tintIndex 1) further forward in Z so they clearly clear the frame. The
// push is in the same direction vanilla's FLUID_TRANSFORM already used, just large enough for 1.21.1.
public class FragileFluidShardModel implements IDynamicBakedModel {

    // Extra forward (toward-viewer, +Z) offset for the fluid layer, in block-model units. Vanilla's
    // FLUID_TRANSFORM already pushes it ~0.001 the same way; this amplifies that to clear z-fighting.
    private static final float FLUID_Z_PUSH = 0.02f;

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
        return vanilla.getRenderTypes(stack, fabulous);
    }

    // Resolves the vanilla per-fluid model (keeping mask/animation/tint), then wraps it to offset
    // the fluid layer.
    private final class ShardOverrides extends ItemOverrides {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level,
                LivingEntity entity, int seed) {
            BakedModel resolved = vanilla.getOverrides().resolve(vanilla, stack, level, entity, seed);
            if (resolved == null) {
                resolved = vanilla;
            }
            return new OffsetModel(resolved);
        }
    }

    // Serves the resolved vanilla quads, but pushes the fluid layer (tintIndex 1) forward so it does
    // not z-fight the frame (tintIndex 0). Everything else delegates to the resolved model.
    private final class OffsetModel implements IDynamicBakedModel {
        private final BakedModel resolved;

        OffsetModel(BakedModel resolved) {
            this.resolved = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                ModelData data, RenderType renderType) {
            List<BakedQuad> in = resolved.getQuads(state, side, rand, data, renderType);
            if (in.isEmpty()) {
                return in;
            }
            List<BakedQuad> out = new ArrayList<>(in.size());
            for (BakedQuad q : in) {
                out.add(q.getTintIndex() == 1 ? pushZ(q, FLUID_Z_PUSH) : q);
            }
            return out;
        }

        @Override public boolean useAmbientOcclusion() { return resolved.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return resolved.isGui3d(); }
        @Override public boolean usesBlockLight() { return resolved.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return resolved.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return resolved.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return resolved.getRenderTypes(stack, fabulous);
        }
    }

    // Returns a copy of q with every vertex translated by dz along Z. The baked vertex array is
    // DefaultVertexFormat.BLOCK (stride = length/4 ints per vertex); position is the first three ints
    // (x, y, z) of each vertex.
    private static BakedQuad pushZ(BakedQuad q, float dz) {
        int[] v = q.getVertices().clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int zIdx = i * stride + 2;
            v[zIdx] = Float.floatToRawIntBits(Float.intBitsToFloat(v[zIdx]) + dz);
        }
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(),
                q.hasAmbientOcclusion());
    }
}*/
//?}
