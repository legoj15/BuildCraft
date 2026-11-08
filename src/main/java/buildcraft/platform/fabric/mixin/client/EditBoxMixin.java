/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import buildcraft.platform.fabric.client.FabricEditBoxShadow;
^///?}
import net.minecraft.client.gui.components.EditBox;

/^*
 * NeoForge's {@code EditBox.setTextShadow} on 1.21.1 (vanilla has it from 1.21.2): each text draw of the box goes
 * through the shadow-taking overload, with the box's own switch ({@code ClientVanillaExtensions#setTextShadow}). This
 * class carries nothing on the later lines.
 *^/
//? if <1.21.10 {
/^@Mixin(EditBox.class)
abstract class EditBoxMixin implements FabricEditBoxShadow {
    @Unique
    private boolean buildcraft$textShadow = true;

    @Override
    public void buildcraft$setTextShadow(boolean shadow) {
        buildcraft$textShadow = shadow;
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int buildcraft$sequence(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
            int colour, Operation<Integer> original) {
        return buildcraft$textShadow ? original.call(graphics, font, text, x, y, colour)
                : graphics.drawString(font, text, x, y, colour, false);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int buildcraft$component(GuiGraphics graphics, Font font, Component text, int x, int y, int colour,
            Operation<Integer> original) {
        return buildcraft$textShadow ? original.call(graphics, font, text, x, y, colour)
                : graphics.drawString(font, text, x, y, colour, false);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int buildcraft$string(GuiGraphics graphics, Font font, String text, int x, int y, int colour,
            Operation<Integer> original) {
        return buildcraft$textShadow ? original.call(graphics, font, text, x, y, colour)
                : graphics.drawString(font, text, x, y, colour, false);
    }
}
^///?} else {
@Mixin(EditBox.class)
abstract class EditBoxMixin {
}
//?}
*///?}
