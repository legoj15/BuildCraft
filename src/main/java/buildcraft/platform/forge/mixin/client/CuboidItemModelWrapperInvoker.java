/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import java.util.List;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.geometry.QuadCollection;
//?} elif >=1.21.10 {
/^import net.minecraft.client.renderer.item.BlockModelWrapper;
^///?} else {
/^import net.minecraft.client.Minecraft;
^///?}

/^*
 * Vanilla's private {@code CuboidItemModelWrapper} constructor, for {@code ForgeFluidContainerModel}'s layers (the Fabric
 * twin has it from the class tweaker; Forge's access transformer leaves it private). An invoker has no injection point,
 * so nothing here can drift with Forge's patches.
 *
 * <p>1.21.11 has no {@code CuboidItemModelWrapper}: its fluid container model ({@code ForgeLegacyFluidContainerModel})
 * draws its own layers, as the Fabric twin does, so there the mixin is empty, on that line's item-layer model
 * ({@code BlockModelWrapper}). 1.21.1 has no such class either (its item models are classic baked models): there it
 * targets {@code Minecraft} and carries nothing. Both keep the class so the mixin config names the same classes on every
 * Forge line.
 *^/
//? if >=26.1 {
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperInvoker {
    @Invoker("<init>")
    static CuboidItemModelWrapper buildcraft$create(List<ItemTintSource> tints, QuadCollection quads,
            ModelRenderProperties properties, Matrix4fc transformation) {
        throw new AssertionError("mixin invoker");
    }
}
//?} elif >=1.21.10 {
/^@Mixin(BlockModelWrapper.class)
public interface CuboidItemModelWrapperInvoker {
}
^///?} else {
/^@Mixin(Minecraft.class)
public interface CuboidItemModelWrapperInvoker {
}
^///?}
*///?}
