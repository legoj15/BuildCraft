/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.render;

import net.minecraft.world.level.block.entity.BlockEntity;

/** How a block entity hands render data to its block model, in the loader's own wrapping. */
public interface RenderDataServices {
    /**
     * The loader's render-data object carrying {@code data}: what the block entity's render-data hook returns
     * (NeoForge: a {@code ModelData} holding it, which the caller casts; Fabric: {@code data} itself).
     */
    Object blockEntityRenderData(PipeRenderData data);

    /**
     * Tells the loader the block entity's render data changed (NeoForge: {@code requestModelDataUpdate}, so it is
     * re-read before the next re-mesh). Fabric reads render data afresh on every re-mesh, so there it does nothing.
     */
    void requestRenderDataUpdate(BlockEntity blockEntity);
}
