/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && >=1.21.10 && <26.1 {
/*import buildcraft.platform.client.LegacyFluidContainerModel;
import buildcraft.platform.forge.mixin.client.ItemModelsAccessor;

/^*
 * MinecraftForge's registration of {@link LegacyFluidContainerModel}, the fluid-container item model type on the one
 * Forge line before 26.1 with item model definitions (1.21.11): Forge has no registration event for item model
 * types (NeoForge's {@code RegisterItemModelsEvent}) and its access transformer leaves vanilla's map private, so
 * the {@code ItemModelsAccessor} mixin reaches it. The bake itself is the shared, loader-neutral one.
 *^/
public final class ForgeLegacyFluidContainerModel {
    private ForgeLegacyFluidContainerModel() {}

    /^* Makes the model type known to item model JSON; called once from the client init (mod construction). *^/
    public static void register() {
        ItemModelsAccessor.buildcraft$idMapper().put(LegacyFluidContainerModel.ID,
                LegacyFluidContainerModel.Unbaked.MAP_CODEC);
    }
}
*///?}
