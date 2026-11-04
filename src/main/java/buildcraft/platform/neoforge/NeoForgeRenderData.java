/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import javax.annotation.Nullable;

import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

import buildcraft.platform.render.PipeRenderData;
import buildcraft.platform.render.RenderDataServices;

/** {@link RenderDataServices} on NeoForge: the render data rides in the block entity's {@link ModelData}. */
public final class NeoForgeRenderData implements RenderDataServices {
    /** The one property every BC render data travels under. */
    private static final ModelProperty<PipeRenderData> PIPE = new ModelProperty<>();

    @Override
    public Object blockEntityRenderData(PipeRenderData data) {
        return ModelData.builder().with(PIPE, data).build();
    }

    @Override
    public void requestRenderDataUpdate(BlockEntity blockEntity) {
        blockEntity.requestModelDataUpdate();
    }

    /** The pipe render data in {@code modelData}, or null when it carries none. */
    @Nullable
    public static PipeRenderData pipe(ModelData modelData) {
        return modelData.get(PIPE);
    }
}
//?}
