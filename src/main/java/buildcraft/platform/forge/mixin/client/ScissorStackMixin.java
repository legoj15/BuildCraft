/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import buildcraft.platform.forge.client.ForgeScissorPeek;
//?} else {
/^import net.minecraft.client.Minecraft;
^///?}

/^*
 * Makes {@code GuiGraphicsExtractor}'s private {@code ScissorStack} a {@link ForgeScissorPeek}, for
 * {@code ClientVanillaExtensions#peekScissor}: Forge adds a public getter for the stack but leaves its class private,
 * so the class's public {@code peek()} cannot be called from outside (NeoForge patches in {@code peekScissorStack()}).
 * No injection point: the duck method only calls the class's own {@code peek()}.
 *
 * <p>1.21.1 has no such class and no caller of the peek: there it targets {@code Minecraft} and carries nothing.
 *^/
//? if >=1.21.10 {
@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack")
abstract class ScissorStackMixin implements ForgeScissorPeek {
    @Shadow
    public abstract ScreenRectangle peek();

    @Override
    public ScreenRectangle buildcraft$peek() {
        return peek();
    }
}
//?} else {
/^@Mixin(Minecraft.class)
abstract class ScissorStackMixin {
}
^///?}
*///?}
