/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import buildcraft.platform.forge.client.ForgeScissorPeek;

/^*
 * Makes {@code GuiGraphicsExtractor}'s private {@code ScissorStack} a {@link ForgeScissorPeek}, for
 * {@code ClientVanillaExtensions#peekScissor}: Forge adds a public getter for the stack but leaves its class private,
 * so the class's public {@code peek()} cannot be called from outside (NeoForge patches in {@code peekScissorStack()}).
 * No injection point: the duck method only calls the class's own {@code peek()}.
 *^/
@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack")
abstract class ScissorStackMixin implements ForgeScissorPeek {
    @Shadow
    public abstract ScreenRectangle peek();

    @Override
    public ScreenRectangle buildcraft$peek() {
        return peek();
    }
}
*///?}
