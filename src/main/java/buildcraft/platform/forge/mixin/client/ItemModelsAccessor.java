/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
//?} else {
/^import net.minecraft.client.Minecraft;
^///?}

/^*
 * Vanilla's item-model type map, so {@code ForgeFluidContainerModel} can register its type: Forge has no registration
 * event for item model types (NeoForge's {@code RegisterItemModelsEvent}) and its access transformer leaves the map
 * private. An accessor has no injection point, so nothing here can drift with Forge's patches.
 *
 * <p>1.21.1 has no such class: there it targets {@code Minecraft} and carries nothing.
 *^/
//? if >=1.21.10 {
@Mixin(ItemModels.class)
public interface ItemModelsAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> buildcraft$idMapper() {
        throw new AssertionError("mixin accessor");
    }
}
//?} else {
/^@Mixin(Minecraft.class)
public interface ItemModelsAccessor {
}
^///?}
*///?}
