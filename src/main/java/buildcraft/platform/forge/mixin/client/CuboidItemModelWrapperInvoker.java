/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge && >=1.21.10 {
/*import java.util.List;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.geometry.QuadCollection;

/^*
 * Vanilla's private {@code CuboidItemModelWrapper} constructor, for {@code ForgeFluidContainerModel}'s layers (the Fabric
 * twin has it from the class tweaker; Forge's access transformer leaves it private). An invoker has no injection point,
 * so nothing here can drift with Forge's patches.
 *^/
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperInvoker {
    @Invoker("<init>")
    static CuboidItemModelWrapper buildcraft$create(List<ItemTintSource> tints, QuadCollection quads,
            ModelRenderProperties properties, Matrix4fc transformation) {
        throw new AssertionError("mixin invoker");
    }
}
*///?}
