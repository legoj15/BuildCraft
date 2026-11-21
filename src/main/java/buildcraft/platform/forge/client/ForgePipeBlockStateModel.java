/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import java.util.List;

//? if >=1.21.10 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
//?} else {
/^import java.util.ArrayList;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
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
 * <p>On 1.21.1 (Forge 52) it is a classic {@code IDynamicBakedModel}, as on NeoForge 1.21.1: the chunk compiler hands
 * {@code getQuads} the block entity's model data and asks once per render type the model names (cutout for the body,
 * translucent for the paint and translucent pluggables), so each list is served on its own pass; a caller with no
 * render type (an item or a lone-block draw) gets both.
 *^/
//? if >=1.21.10 {
final class ForgePipeBlockStateModel implements BlockStateModel {
    private final BlockStateModel vanillaDelegate;
    private final PipeModelQuads quads;

    ForgePipeBlockStateModel(BlockStateModel vanillaDelegate, PipeModelQuads quads) {
        this.vanillaDelegate = vanillaDelegate;
        this.quads = quads;
    }

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
}
//?} else {
/^final class ForgePipeBlockStateModel implements IDynamicBakedModel {
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(
            net.minecraft.client.renderer.rendertype.RenderTypes.cutout(),
            net.minecraft.client.renderer.rendertype.RenderTypes.translucent());

    private final BakedModel vanillaDelegate;
    private final PipeModelQuads quads;

    ForgePipeBlockStateModel(BakedModel vanillaDelegate, PipeModelQuads quads) {
        this.vanillaDelegate = vanillaDelegate;
        this.quads = quads;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData modelData,
            RenderType renderType) {
        // Pipe quads are sub-block sized and unculled: the general (null-side) pass only.
        if (side != null) {
            return List.of();
        }
        PipeRenderData data = ForgeRenderData.pipe(modelData);
        if (data == null || !quads.hasGeometry(data)) {
            return vanillaDelegate.getQuads(state, null, random, modelData, renderType);
        }
        List<BakedQuad> out = new ArrayList<>();
        if (renderType == null || renderType == net.minecraft.client.renderer.rendertype.RenderTypes.cutout()) {
            out.addAll(quads.cutoutQuads(data));
        }
        if (renderType == null || renderType == net.minecraft.client.renderer.rendertype.RenderTypes.translucent()) {
            out.addAll(quads.translucentQuads(data));
        }
        return out;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData modelData) {
        return LAYERS;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return vanillaDelegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return vanillaDelegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return vanillaDelegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    // Abstract and deprecated on Forge 52 (its model-data overload falls back to it), so it must be overridden.
    @SuppressWarnings("deprecation")
    @Override
    public TextureAtlasSprite getParticleIcon() {
        return vanillaDelegate.getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData modelData) {
        return vanillaDelegate.getParticleIcon(modelData);
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
^///?}
*///?}
