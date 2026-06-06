/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

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

import buildcraft.transport.tile.TilePipeHolder;

/**
 * A BlockStateModel for the pipe_holder block.
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
 * <p>The tile entity's PipeModelKey is accessed via ModelData, which is
 * populated by TilePipeHolder.getModelData() and refreshed whenever
 * pipe connections or paint change.
 */
@SuppressWarnings("deprecation")
//? if >=1.21.10 {
public class PipeBlockStateModel implements DynamicBlockStateModel {
    private final BlockStateModel vanillaDelegate;

    public PipeBlockStateModel(BlockStateModel vanillaDelegate) {
        this.vanillaDelegate = vanillaDelegate;
    }

    @Override
    //? if >=26.1 {
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> parts) {
    //?} else {
    /*public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockModelPart> parts) {*/
    //?}
        // Get the tile reference from model data
        ModelData modelData = level.getModelData(pos);
        TilePipeHolder tile = modelData.get(TilePipeHolder.PIPE_MODEL_DATA);
        if (tile == null || tile.getPipe() == null) {
            // No pipe data — fall back to vanilla delegate (particle texture only)
            vanillaDelegate.collectParts(random, parts);
            return;
        }

        // Particle source: 26.1 exposes a Material.Baked, 1.21.11 a TextureAtlasSprite.
        //? if >=26.1 {
        Material.Baked particle = vanillaDelegate.particleMaterial();
        //?} else {
        /*TextureAtlasSprite particle = vanillaDelegate.particleIcon();*/
        //?}

        // Cutout pass — pipe body geometry with pipe-specific textures
        List<BakedQuad> cutoutQuads = PipeModelCacheAll.getCutoutModel(tile);
        if (!cutoutQuads.isEmpty()) {
            addQuadsAsPart(parts, cutoutQuads, particle, net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
        }

        // Translucent pass — colour overlay for painted pipes. On 26.1 the quad's MaterialInfo
        // layer routes it to the alpha-blended buffer; 1.21.11 BakedQuads carry no layer (see the
        // MutableQuad.toBakedTranslucent TODO), so this currently shares the cutout buffer there.
        List<BakedQuad> translucentQuads = PipeModelCacheAll.getTranslucentModel(tile);
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
        ModelData modelData = level.getModelData(pos);
        TilePipeHolder tile = modelData.get(TilePipeHolder.PIPE_MODEL_DATA);
        if (tile == null || tile.getPipe() == null) {
            return EMPTY_KEY;
        }
        // Use PipeAllCutoutKey as the geometry key — it encodes both the pipe model
        // and all pluggable model keys, so the chunk rebuilds when either changes.
        return new PipeModelCacheAll.PipeAllCutoutKey(tile);
    }

    //? if >=26.1 {
    @Override
    public Material.Baked particleMaterial() {
        return vanillaDelegate.particleMaterial();
    }
    //?} else {
    /*@Override
    public TextureAtlasSprite particleIcon() {
        return vanillaDelegate.particleIcon();
    }*/
    //?}

    // materialFlags() exists only on 26.1's BlockStateModel; 1.21.11 has no such method.
    //? if >=26.1 {
    @Override
    public int materialFlags() {
        return vanillaDelegate.materialFlags();
    }
    //?}
}
//?} else {
/*// 1.21.1 has no DynamicBlockStateModel / collectParts — the dynamic block model is a classic
// IDynamicBakedModel whose getQuads() returns the same shared PipeModelCacheAll quad lists the
// modern collectParts() wraps into BlockModelParts. Behaviour matches the 1.21.10/11 nodes
// (translucent overlay shares the cutout buffer; no per-quad chunk layer on these lines).
public class PipeBlockStateModel implements net.neoforged.neoforge.client.model.IDynamicBakedModel {
    private final net.minecraft.client.resources.model.BakedModel vanillaDelegate;

    public PipeBlockStateModel(net.minecraft.client.resources.model.BakedModel vanillaDelegate) {
        this.vanillaDelegate = vanillaDelegate;
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
            BlockState state, net.minecraft.core.Direction side, RandomSource random,
            ModelData extraData, net.minecraft.client.renderer.RenderType renderType) {
        // Pipe quads are sub-block and unculled — only emit on the general (null-side) pass.
        if (side != null) {
            return java.util.List.of();
        }
        TilePipeHolder tile = extraData.get(TilePipeHolder.PIPE_MODEL_DATA);
        if (tile == null || tile.getPipe() == null) {
            return vanillaDelegate.getQuads(state, side, random, extraData, renderType);
        }
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> out = new java.util.ArrayList<>();
        // Split by chunk layer: the pipe body rides the cutout pass, the painted-pipe colour overlay
        // rides the translucent pass so its 30%-tint mask alpha-blends instead of rendering opaque.
        // (renderType is null for non-chunk callers — emit everything in that case.)
        if (renderType == null || renderType == net.minecraft.client.renderer.RenderType.cutout()) {
            out.addAll(PipeModelCacheAll.getCutoutModel(tile));
        }
        if (renderType == null || renderType == net.minecraft.client.renderer.RenderType.translucent()) {
            out.addAll(PipeModelCacheAll.getTranslucentModel(tile));
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
                net.minecraft.client.renderer.RenderType.cutout(),
                net.minecraft.client.renderer.RenderType.translucent());
    }

    @Override
    public boolean useAmbientOcclusion() { return vanillaDelegate.useAmbientOcclusion(); }
    @Override
    public boolean isGui3d() { return vanillaDelegate.isGui3d(); }
    @Override
    public boolean usesBlockLight() { return vanillaDelegate.usesBlockLight(); }
    @Override
    public boolean isCustomRenderer() { return false; }
    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return vanillaDelegate.getParticleIcon();
    }
    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
    }
}*/
//?}
