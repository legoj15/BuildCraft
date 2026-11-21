/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import buildcraft.platform.forge.client.ForgeEditBoxShadow;
^///?}
import net.minecraft.client.gui.components.EditBox;

/^*
 * NeoForge's {@code EditBox.setTextShadow} on 1.21.1 (vanilla has it from 1.21.2; Forge 52 does not patch it in): each
 * text draw of the box goes through the shadow-taking overload, with the box's own switch
 * ({@code ClientVanillaExtensions#setTextShadow}). This class carries nothing on 26.3.
 *
 * <p><b>Injection points.</b> Every {@code GuiGraphics.drawString} call in {@code renderWidget}, one redirect per
 * overload (the text before and after the cursor as a {@code FormattedCharSequence}, the hint as a {@code Component},
 * the suggestion and the cursor as {@code String}s). Forge 52 does not patch {@code EditBox}. Plain redirects, as Forge
 * 52 ships no MixinExtras (the Fabric twin wraps the same calls).
 *^/
//? if <1.21.10 {
/^@Mixin(EditBox.class)
abstract class EditBoxMixin implements ForgeEditBoxShadow {
    @Unique
    private boolean buildcraft$textShadow = true;

    @Override
    public void buildcraft$setTextShadow(boolean shadow) {
        buildcraft$textShadow = shadow;
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int buildcraft$sequence(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
            int colour) {
        return graphics.drawString(font, text, x, y, colour, buildcraft$textShadow);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int buildcraft$component(GuiGraphics graphics, Font font, Component text, int x, int y, int colour) {
        return graphics.drawString(font, text, x, y, colour, buildcraft$textShadow);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int buildcraft$string(GuiGraphics graphics, Font font, String text, int x, int y, int colour) {
        return graphics.drawString(font, text, x, y, colour, buildcraft$textShadow);
    }
}
^///?} else {
@Mixin(EditBox.class)
abstract class EditBoxMixin {
}
//?}
*///?}
