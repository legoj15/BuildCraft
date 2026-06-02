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
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;*/
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
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
            addQuadsAsPart(parts, cutoutQuads, particle);
        }

        // Translucent pass — colour overlay for painted pipes. On 26.1 the quad's MaterialInfo
        // layer routes it to the alpha-blended buffer; 1.21.11 BakedQuads carry no layer (see the
        // MutableQuad.toBakedTranslucent TODO), so this currently shares the cutout buffer there.
        List<BakedQuad> translucentQuads = PipeModelCacheAll.getTranslucentModel(tile);
        if (!translucentQuads.isEmpty()) {
            addQuadsAsPart(parts, translucentQuads, particle);
        }
    }

    /** Convert a list of BakedQuads into a cutout model part and add to the parts list.
     *  All pipe quads are unculled (sub-block sized, shouldn't be face-culled). */
    //? if >=26.1 {
    private static void addQuadsAsPart(List<BlockStateModelPart> parts, List<BakedQuad> quads, Material.Baked particle) {
    //?} else {
    /*private static void addQuadsAsPart(List<BlockModelPart> parts, List<BakedQuad> quads, TextureAtlasSprite particle) {*/
    //?}
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads) {
            builder.addUnculledFace(quad);
        }
        //? if >=26.1 {
        parts.add(new net.minecraft.client.resources.model.SimpleModelWrapper(builder.build(), true, particle));
        //?} else {
        /*parts.add(new net.minecraft.client.renderer.block.model.SimpleModelWrapper(builder.build(), true, particle));*/
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
