/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.platform.render.PipeRenderData;
import buildcraft.platform.render.RenderDataServices;

/^*
 * {@link RenderDataServices} on Fabric: the render data is what the block entity's
 * {@code RenderDataBlockEntity#getRenderData} returns, and the block model reads it back through
 * {@code FabricBlockGetter#getBlockEntityRenderData}.
 *^/
public final class FabricRenderData implements RenderDataServices {
    @Override
    public Object blockEntityRenderData(PipeRenderData data) {
        return data;
    }

    @Override
    public void requestRenderDataUpdate(BlockEntity blockEntity) {
        // Fabric re-reads getRenderData whenever the section re-meshes; there is no cache to invalidate.
    }
}
*///?}
