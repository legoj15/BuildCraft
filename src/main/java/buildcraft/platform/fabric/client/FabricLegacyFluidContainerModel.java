/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && >=1.21.10 && <26.1 {
/*import net.minecraft.client.renderer.item.ItemModels;

import buildcraft.platform.client.LegacyFluidContainerModel;

/^*
 * Fabric's registration of {@link LegacyFluidContainerModel}, the fluid-container item model type on the lines
 * before 26.1: on Fabric the type goes straight into vanilla's item-model type map. The bake itself is the shared,
 * loader-neutral one.
 *^/
public final class FabricLegacyFluidContainerModel {
    private FabricLegacyFluidContainerModel() {}

    /^* Makes the model type known to item model JSON; called once from the Fabric client initializer. *^/
    public static void register() {
        ItemModels.ID_MAPPER.put(LegacyFluidContainerModel.ID, LegacyFluidContainerModel.Unbaked.MAP_CODEC);
    }
}
*///?}
