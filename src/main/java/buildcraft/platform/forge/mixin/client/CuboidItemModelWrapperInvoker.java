/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import java.util.List;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.geometry.QuadCollection;
//?} else {
/^import net.minecraft.client.Minecraft;
^///?}

/^*
 * Vanilla's private {@code CuboidItemModelWrapper} constructor, for {@code ForgeFluidContainerModel}'s layers (the Fabric
 * twin has it from the class tweaker; Forge's access transformer leaves it private). An invoker has no injection point,
 * so nothing here can drift with Forge's patches.
 *
 * <p>1.21.1 has no such class (its item models are classic baked models): there it targets {@code Minecraft} and carries nothing.
 *^/
//? if >=1.21.10 {
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperInvoker {
    @Invoker("<init>")
    static CuboidItemModelWrapper buildcraft$create(List<ItemTintSource> tints, QuadCollection quads,
            ModelRenderProperties properties, Matrix4fc transformation) {
        throw new AssertionError("mixin invoker");
    }
}
//?} else {
/^@Mixin(Minecraft.class)
public interface CuboidItemModelWrapperInvoker {
}
^///?}
*///?}
