/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.render.PipeRenderData;

/^*
 * The block model {@code ClientRegistrationSinks#pipeBlockModel} swaps in on Fabric: emits the {@link PipeModelQuads}
 * of the block entity's {@link PipeRenderData} (read through {@code FabricBlockGetter#getBlockEntityRenderData}, which
 * returns what its {@code RenderDataBlockEntity#getRenderData} handed over) into the chunk mesh, unculled, each list on
 * its own layer (the body and opaque pluggables cutout; a painted pipe's overlay, lens glass and translucent facades
 * translucent); the wrapped model where there is none.
 *
 * <p>Vanilla's {@code BakedQuad} carries no per-vertex colour on Fabric (see {@code ClientModels#withVertexColours}), so
 * each quad's colours (BC's directional shading, a painted overlay's alpha, lens glass) are put back on the emitter from
 * {@link FabricClientModels}' side table.
 *^/
final class FabricPipeBlockModel extends WrapperBlockStateModel {
    private final PipeModelQuads quads;

    FabricPipeBlockModel(BlockStateModel wrapped, PipeModelQuads quads) {
        super(wrapped);
        this.quads = quads;
    }

    private PipeRenderData renderData(BlockAndTintGetter level, BlockPos pos) {
        return level.getBlockEntityRenderData(pos) instanceof PipeRenderData data && quads.hasGeometry(data)
                ? data : null;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random, Predicate<Direction> cullTest) {
        PipeRenderData data = renderData(level, pos);
        if (data == null) {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        emit(emitter, quads.cutoutQuads(data), ChunkSectionLayer.CUTOUT);
        emit(emitter, quads.translucentQuads(data), ChunkSectionLayer.TRANSLUCENT);
    }

    private static void emit(QuadEmitter emitter, List<BakedQuad> list, ChunkSectionLayer layer) {
        for (BakedQuad quad : list) {
            // No cull face: pipe geometry is sub-block sized, like NeoForge's addUnculledFace.
            emitter.fromBakedQuad(quad);
            emitter.chunkLayer(layer);
            FabricClientModels.applyVertexColours(emitter, quad);
            emitter.emit();
        }
    }

    /^* The geometry the mesh gets, keyed: the pipe's own quads, or (as {@link #emitQuads} draws) the wrapped model's,
     *  under that model's key. *^/
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        PipeRenderData data = renderData(level, pos);
        return data == null ? super.createGeometryKey(level, pos, state, random) : quads.geometryKey(data);
    }
}
*///?}
