/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.platform.neoforge.client;

//? if neoforge {
import java.util.List;

//? if >=26.1 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
//?} elif >=1.21.10 {
/*import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;*/
//?}
// (1.21.1 has no block-model-STATE system — the IDynamicBakedModel class below fully-qualifies its types.)
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

//? if >=1.21.10 {
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
//?}
import net.neoforged.neoforge.model.data.ModelData;

import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.neoforge.NeoForgeRenderData;
import buildcraft.platform.render.PipeRenderData;

/**
 * The block model {@code ClientRegistrationSinks#pipeBlockModel} swaps in for every state of the pipe holder.
 *
 * <p>Implements NeoForge's DynamicBlockStateModel to receive level/pos context
 * in collectParts(), enabling position-dependent pipe geometry to be rendered
 * in the chunk mesh with proper face culling, AO, and directional shading.
 *
 * <p>Cutout pipe body quads carry {@code ChunkSectionLayer.CUTOUT} in their
 * {@code BakedQuad.MaterialInfo}, while translucent colour overlay quads carry
 * {@code ChunkSectionLayer.TRANSLUCENT}. The chunk compiler routes each quad
 * to its correct buffer based on this per-quad layer field.
 *
 * <p>The block entity's {@link PipeRenderData} is reached via ModelData, which is
 * populated by the block entity's getModelData() and refreshed whenever
 * pipe connections or paint change; the {@link PipeModelQuads} turn it into quads.
 */
//? if >=1.21.10 {
final class NeoForgePipeBlockStateModel implements DynamicBlockStateModel {
    private final BlockStateModel vanillaDelegate;
    private final PipeModelQuads quads;

    NeoForgePipeBlockStateModel(BlockStateModel vanillaDelegate, PipeModelQuads quads) {
        this.vanillaDelegate = vanillaDelegate;
        this.quads = quads;
    }

    @Override
    //? if >=26.1 {
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> parts) {
    //?} else {
    /*public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockModelPart> parts) {*/
    //?}
        // Get the pipe render data from model data
        PipeRenderData data = NeoForgeRenderData.pipe(level.getModelData(pos));
        if (data == null || !quads.hasGeometry(data)) {
            // No pipe data — fall back to vanilla delegate (particle texture only)
            vanillaDelegate.collectParts(level, pos, state, random, parts);
            return;
        }

        // Particle source: 26.1 exposes a Material.Baked, 1.21.11 a TextureAtlasSprite.
        //? if >=26.1 {
        Material.Baked particle = vanillaDelegate.particleMaterial(level, pos, state);
        //?} else {
        /*TextureAtlasSprite particle = vanillaDelegate.particleIcon(level, pos, state);*/
        //?}

        // Cutout pass — pipe body geometry with pipe-specific textures
        List<BakedQuad> cutoutQuads = quads.cutoutQuads(data);
        if (!cutoutQuads.isEmpty()) {
            addQuadsAsPart(parts, cutoutQuads, particle, net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
        }

        // Translucent pass — colour overlay for painted pipes. On 26.1 the quad's MaterialInfo
        // layer routes it to the alpha-blended buffer; 1.21.11 BakedQuads carry no layer (see the
        // MutableQuad.toBakedTranslucent TODO), so this currently shares the cutout buffer there.
        List<BakedQuad> translucentQuads = quads.translucentQuads(data);
        if (!translucentQuads.isEmpty()) {
            addQuadsAsPart(parts, translucentQuads, particle, net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        }
    }

    /** Convert a list of BakedQuads into a cutout model part and add to the parts list.
     *  All pipe quads are unculled (sub-block sized, shouldn't be face-culled). */
    //? if >=26.1 {
    private static void addQuadsAsPart(List<BlockStateModelPart> parts, List<BakedQuad> quads, Material.Baked particle, net.minecraft.client.renderer.chunk.ChunkSectionLayer layer) {
    //?} else {
    /*private static void addQuadsAsPart(List<BlockModelPart> parts, List<BakedQuad> quads, TextureAtlasSprite particle, net.minecraft.client.renderer.chunk.ChunkSectionLayer layer) {*/
    //?}
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads) {
            builder.addUnculledFace(quad);
        }
        //? if >=26.1 {
        // layer unused here: 26.1 routes via the quad's BakedQuad.MaterialInfo layer.
        parts.add(new net.minecraft.client.resources.model.SimpleModelWrapper(builder.build(), true, particle));
        //?} else {
        /*parts.add(new net.minecraft.client.renderer.block.model.SimpleModelWrapper(builder.build(), true, particle, layer));*/
        //?}
    }

    /** Sentinel key returned when there is no pipe model data. */
    private static final Object EMPTY_KEY = new Object();

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        PipeRenderData data = NeoForgeRenderData.pipe(level.getModelData(pos));
        if (data == null || !quads.hasGeometry(data)) {
            return EMPTY_KEY;
        }
        // The key encodes both the pipe model and all pluggable model keys, so the chunk rebuilds when either changes.
        return quads.geometryKey(data);
    }

    // The context-free particle/material-flag getters are deprecated in favour of NeoForge's level-aware ones, but
    // BlockStateModel still declares them abstract, so a model must implement them (and can only delegate in kind).
    //? if >=26.1 {
    @SuppressWarnings("deprecation")
    @Override
    public Material.Baked particleMaterial() {
        return vanillaDelegate.particleMaterial();
    }
    //?} else {
    /*@SuppressWarnings("deprecation")
    @Override
    public TextureAtlasSprite particleIcon() {
        return vanillaDelegate.particleIcon();
    }*/
    //?}

    // materialFlags() exists only on 26.1's BlockStateModel; 1.21.11 has no such method.
    //? if >=26.1 {
    @SuppressWarnings("deprecation")
    @Override
    public int materialFlags() {
        return vanillaDelegate.materialFlags();
    }
    //?}
}
//?} else {
/*// 1.21.1 has no DynamicBlockStateModel / collectParts — the dynamic block model is a classic
// IDynamicBakedModel whose getQuads() returns the same shared quad lists the
// modern collectParts() wraps into BlockModelParts. Behaviour matches the 1.21.10/11 nodes
// (translucent overlay shares the cutout buffer; no per-quad chunk layer on these lines).
final class NeoForgePipeBlockStateModel implements net.neoforged.neoforge.client.model.IDynamicBakedModel {
    private final net.minecraft.client.resources.model.BakedModel vanillaDelegate;
    private final PipeModelQuads quads;

    NeoForgePipeBlockStateModel(net.minecraft.client.resources.model.BakedModel vanillaDelegate, PipeModelQuads quads) {
        this.vanillaDelegate = vanillaDelegate;
        this.quads = quads;
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
            BlockState state, net.minecraft.core.Direction side, RandomSource random,
            ModelData extraData, net.minecraft.client.renderer.rendertype.RenderType renderType) {
        // Pipe quads are sub-block and unculled — only emit on the general (null-side) pass.
        if (side != null) {
            return java.util.List.of();
        }
        PipeRenderData data = NeoForgeRenderData.pipe(extraData);
        if (data == null || !quads.hasGeometry(data)) {
            return vanillaDelegate.getQuads(state, side, random, extraData, renderType);
        }
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> out = new java.util.ArrayList<>();
        // Split by chunk layer: the pipe body rides the cutout pass, the painted-pipe colour overlay
        // rides the translucent pass so its 30%-tint mask alpha-blends instead of rendering opaque.
        // (renderType is null for non-chunk callers — emit everything in that case.)
        if (renderType == null || renderType == net.minecraft.client.renderer.rendertype.RenderTypes.cutout()) {
            out.addAll(quads.cutoutQuads(data));
        }
        if (renderType == null || renderType == net.minecraft.client.renderer.rendertype.RenderTypes.translucent()) {
            out.addAll(quads.translucentQuads(data));
        }
        return out;
    }

    @Override
    public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(
            BlockState state, RandomSource rand, ModelData data) {
        // Pipe body on the cutout layer; painted-pipe colour overlay on the translucent layer so it
        // alpha-blends (otherwise the 30%-tint mask renders opaque). Matches the modern nodes, which
        // route the translucent part to the translucent chunk layer via collectParts.
        return net.neoforged.neoforge.client.ChunkRenderTypeSet.of(
                net.minecraft.client.renderer.rendertype.RenderTypes.cutout(),
                net.minecraft.client.renderer.rendertype.RenderTypes.translucent());
    }

    @Override
    public boolean useAmbientOcclusion() { return vanillaDelegate.useAmbientOcclusion(); }
    @Override
    public boolean isGui3d() { return vanillaDelegate.isGui3d(); }
    @Override
    public boolean usesBlockLight() { return vanillaDelegate.usesBlockLight(); }
    @Override
    public boolean isCustomRenderer() { return false; }
    // Abstract on 1.21.1's BakedModel yet deprecated there; NeoForge's ModelData overload is the real hook.
    @SuppressWarnings("deprecation")
    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return vanillaDelegate.getParticleIcon(ModelData.EMPTY);
    }
    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(ModelData data) {
        return vanillaDelegate.getParticleIcon(data);
    }
    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
    }
}*/
//?}
//?}
