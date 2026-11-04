/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.model;

import java.util.List;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

import buildcraft.platform.client.PipeModelQuads;
import buildcraft.platform.render.PipeRenderData;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * The pipe holder's chunk-mesh quads, from the {@link PipeRenderData} its {@link TilePipeHolder} hands the block
 * model: the joined pipe-body and pluggable caches of {@link PipeModelCacheAll}.
 */
public enum PipeBlockModelQuads implements PipeModelQuads {
    INSTANCE;

    @Override
    public boolean hasGeometry(PipeRenderData data) {
        return data instanceof TilePipeHolder tile && tile.getPipe() != null;
    }

    @Override
    public Object geometryKey(PipeRenderData data) {
        // The cutout key encodes both the pipe model and all pluggable model keys, so the chunk rebuilds when
        // either changes.
        return new PipeModelCacheAll.PipeAllCutoutKey((TilePipeHolder) data);
    }

    @Override
    public List<BakedQuad> cutoutQuads(PipeRenderData data) {
        return PipeModelCacheAll.getCutoutModel((TilePipeHolder) data);
    }

    @Override
    public List<BakedQuad> translucentQuads(PipeRenderData data) {
        return PipeModelCacheAll.getTranslucentModel((TilePipeHolder) data);
    }
}
