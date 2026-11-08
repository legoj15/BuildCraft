/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <1.21.10 {
/*import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.render.PipeRenderData;

/^*
 * The block model {@code ClientRegistrationSinks#pipeBlockModel} swaps in on Fabric 1.21.1, the twin of
 * {@code FabricPipeBlockModel} on Fabric API's first renderer API: emits the {@link PipeModelQuads} of the block
 * entity's {@link PipeRenderData} (read through {@code getBlockEntityRenderData}) into the chunk mesh, unculled, the
 * cutout list on a cutout material and the translucent list on a translucent one; the wrapped model where there is
 * none. Vanilla's 1.21.1 quads still carry their per-vertex colours, which the emitter copies with the vertices.
 *^/
final class FabricClassicPipeBlockModel extends ForwardingBakedModel {
    private final PipeModelQuads quads;

    FabricClassicPipeBlockModel(BakedModel wrapped, PipeModelQuads quads) {
        super(wrapped);
        this.quads = quads;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos,
            Supplier<RandomSource> random, RenderContext context) {
        PipeRenderData data = level.getBlockEntityRenderData(pos) instanceof PipeRenderData d && quads.hasGeometry(d)
                ? d : null;
        if (data == null) {
            super.emitBlockQuads(level, state, pos, random, context);
            return;
        }
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        QuadEmitter emitter = context.getEmitter();
        emit(emitter, quads.cutoutQuads(data), renderer.materialFinder().blendMode(BlendMode.CUTOUT).find());
        emit(emitter, quads.translucentQuads(data), renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT).find());
    }

    private static void emit(QuadEmitter emitter, List<BakedQuad> list, RenderMaterial material) {
        for (BakedQuad quad : list) {
            // No cull face: pipe geometry is sub-block sized, like NeoForge's addUnculledFace.
            emitter.fromVanilla(quad, material, null);
            emitter.emit();
        }
    }
}
*///?}
