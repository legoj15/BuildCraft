/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && >=1.21.10 {
/*//? if <26.1 {
/^import java.util.Collection;
^///?}
import java.util.List;

//? if >=26.1 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
//?} else {
/^import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.world.level.block.state.BlockState;
^///?}
import net.minecraft.util.RandomSource;

import net.minecraftforge.client.model.data.ModelData;

import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.forge.ForgeRenderData;
import buildcraft.platform.render.PipeRenderData;

/^*
 * The block model {@code ClientRegistrationSinks#pipeBlockModel} swaps in for every state of the pipe holder: the pipe's
 * quads, built from the {@link PipeRenderData} the block entity puts in its model data, in the chunk mesh.
 *
 * <p>Differs from the NeoForge twin in where the data comes from: NeoForge's dynamic model is handed the level and
 * position and reads the model data itself; Forge's chunk compiler reads the block entity's model data and hands it to
 * {@code IForgeBlockStateModel}'s three-argument {@code collectParts}. Forge has no geometry key (NeoForge's chunk cache
 * hint): a model data update re-meshes the section ({@code ForgeRenderData}). The quads carry their own chunk layer in
 * their material (cutout body, translucent paint and pluggables), so each list is one part, as on NeoForge 26.x. A call
 * without model data (the context-free {@code collectParts}, or an empty one) draws the vanilla model: the particle only.
 *
 * <p><b>1.21.11.</b> A quad carries no layer there: Forge's chunk compiler asks the model for its layers
 * ({@code getRenderTypes}) and collects the parts once per layer, into that layer's buffer. A pipe with geometry answers
 * cutout and translucent and hands back the matching list for each; a call with no layer (a model draw outside the
 * chunk mesh) gets both. Each part also names its layer ({@code IForgeBlockModelPart.layer}), for the callers that ask.
 *^/
final class ForgePipeBlockStateModel implements BlockStateModel {
    private final BlockStateModel vanillaDelegate;
    private final PipeModelQuads quads;

    ForgePipeBlockStateModel(BlockStateModel vanillaDelegate, PipeModelQuads quads) {
        this.vanillaDelegate = vanillaDelegate;
        this.quads = quads;
    }

    //? if >=26.1 {
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts, ModelData modelData) {
        PipeRenderData data = ForgeRenderData.pipe(modelData);
        if (data == null || !quads.hasGeometry(data)) {
            vanillaDelegate.collectParts(random, parts, modelData);
            return;
        }
        Material.Baked particle = vanillaDelegate.particleMaterial(modelData);
        addQuadsAsPart(parts, quads.cutoutQuads(data), particle);
        addQuadsAsPart(parts, quads.translucentQuads(data), particle);
    }

    /^* Pipe quads are sub-block sized, so all of them are unculled. *^/
    private static void addQuadsAsPart(List<BlockStateModelPart> parts, List<BakedQuad> quads, Material.Baked particle) {
        if (quads.isEmpty()) {
            return;
        }
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads) {
            builder.addUnculledFace(quad);
        }
        parts.add(new SimpleModelWrapper(builder.build(), true, particle));
    }

    // The context-free getters are deprecated by Forge in favour of its model-data ones, but BlockStateModel still
    // declares them abstract, so a model must implement them (and can only delegate in kind).
    @SuppressWarnings("deprecation")
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        vanillaDelegate.collectParts(random, parts);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Material.Baked particleMaterial() {
        return vanillaDelegate.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(ModelData modelData) {
        return vanillaDelegate.particleMaterial(modelData);
    }

    @Override
    public int materialFlags() {
        return vanillaDelegate.materialFlags();
    }
    //?} else {
    /^private static final List<ChunkSectionLayer> PIPE_LAYERS = List.of(ChunkSectionLayer.CUTOUT,
            ChunkSectionLayer.TRANSLUCENT);

    @Override
    public Collection<ChunkSectionLayer> getRenderTypes(BlockState state, RandomSource random, ModelData modelData) {
        PipeRenderData data = ForgeRenderData.pipe(modelData);
        if (data == null || !quads.hasGeometry(data)) {
            return vanillaDelegate.getRenderTypes(state, random, modelData);
        }
        return PIPE_LAYERS;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts, ModelData modelData,
            ChunkSectionLayer layer) {
        PipeRenderData data = ForgeRenderData.pipe(modelData);
        if (data == null || !quads.hasGeometry(data)) {
            vanillaDelegate.collectParts(random, parts, modelData, layer);
            return;
        }
        TextureAtlasSprite particle = vanillaDelegate.particleIcon(modelData);
        if (layer == null || layer == ChunkSectionLayer.CUTOUT) {
            addQuadsAsPart(parts, quads.cutoutQuads(data), particle, ChunkSectionLayer.CUTOUT);
        }
        if (layer == null || layer == ChunkSectionLayer.TRANSLUCENT) {
            addQuadsAsPart(parts, quads.translucentQuads(data), particle, ChunkSectionLayer.TRANSLUCENT);
        }
    }

    // Pipe quads are sub-block sized, so all of them are unculled.
    private static void addQuadsAsPart(List<BlockModelPart> parts, List<BakedQuad> quads, TextureAtlasSprite particle,
            ChunkSectionLayer layer) {
        if (quads.isEmpty()) {
            return;
        }
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads) {
            builder.addUnculledFace(quad);
        }
        parts.add(new SimpleModelWrapper(builder.build(), true, particle, layer, layer));
    }

    // The context-free getters are deprecated by Forge in favour of its model-data ones, but BlockStateModel still
    // declares them abstract, so a model must implement them (and can only delegate in kind).
    @SuppressWarnings("deprecation")
    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts) {
        vanillaDelegate.collectParts(random, parts);
    }

    @SuppressWarnings("deprecation")
    @Override
    public TextureAtlasSprite particleIcon() {
        return vanillaDelegate.particleIcon();
    }

    @Override
    public TextureAtlasSprite particleIcon(ModelData modelData) {
        return vanillaDelegate.particleIcon(modelData);
    }
    ^///?}
}
*///?}
