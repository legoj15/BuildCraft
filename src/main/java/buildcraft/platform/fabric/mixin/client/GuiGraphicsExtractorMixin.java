/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import java.util.List;
import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/^import net.minecraft.client.gui.GuiGraphics;
^///?}
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.fabric.client.FabricTooltipHooks;

/^*
 * The tooltip draw's pre hook ({@link FabricTooltipHooks}): the item-stack tooltip request names its stack, the
 * deferred draw it queues is tagged with it, and the draw fires the listeners before positioning, where NeoForge's
 * patch fires {@code RenderTooltipEvent.Pre}. Before 26.1 the class is {@code GuiGraphics} and the draw is its
 * {@code renderTooltip}. 1.21.1 draws a tooltip at once ({@code renderTooltipInternal}, no deferred draw): the item
 * request is tagged as it is made.
 *^/
//? if >=26.1 {
@Mixin(GuiGraphicsExtractor.class)
//?} else {
/^@Mixin(GuiGraphics.class)
^///?}
abstract class GuiGraphicsExtractorMixin {

    //? if >=26.3 {
    @WrapOperation(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V"))
    private void buildcraft$describeItemTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, boolean extraSpace,
            Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        FabricTooltipHooks.describing(stack, () -> original.call(graphics, font, lines, image, x, y, style, extraSpace));
    }
    //?} elif >=26.1 {
    /^// Before 26.3 the tooltip calls carry no extra-space flag.
    @WrapOperation(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
    private void buildcraft$describeItemTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, Operation<Void> original,
            @Local(argsOnly = true) ItemStack stack) {
        FabricTooltipHooks.describing(stack, () -> original.call(graphics, font, lines, image, x, y, style));
    }
    ^///?} elif >=1.21.10 {
    /^@WrapOperation(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
    private void buildcraft$describeItemTooltip(GuiGraphics graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, Operation<Void> original,
            @Local(argsOnly = true) ItemStack stack) {
        FabricTooltipHooks.describing(stack, () -> original.call(graphics, font, lines, image, x, y, style));
    }
    ^///?} else {
    /^@WrapOperation(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V"))
    private void buildcraft$describeItemTooltip(GuiGraphics graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Operation<Void> original,
            @Local(argsOnly = true) ItemStack stack) {
        FabricTooltipHooks.describing(stack,
                () -> FabricTooltipHooks.tagDeferred(() -> original.call(graphics, font, lines, image, x, y)).run());
    }
    ^///?}

    //? if >=26.3 {
    @WrapOperation(method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;ZZ)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;deferredTooltip:Ljava/lang/Runnable;",
                    opcode = Opcodes.PUTFIELD))
    private void buildcraft$tagDeferredTooltip(GuiGraphicsExtractor graphics, Runnable draw, Operation<Void> original) {
        original.call(graphics, FabricTooltipHooks.tagDeferred(draw));
    }
    //?} elif >=26.1 {
    /^@WrapOperation(method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;deferredTooltip:Ljava/lang/Runnable;",
                    opcode = Opcodes.PUTFIELD))
    private void buildcraft$tagDeferredTooltip(GuiGraphicsExtractor graphics, Runnable draw, Operation<Void> original) {
        original.call(graphics, FabricTooltipHooks.tagDeferred(draw));
    }
    ^///?} elif >=1.21.10 {
    /^@WrapOperation(method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiGraphics;deferredTooltip:Ljava/lang/Runnable;",
                    opcode = Opcodes.PUTFIELD))
    private void buildcraft$tagDeferredTooltip(GuiGraphics graphics, Runnable draw, Operation<Void> original) {
        original.call(graphics, FabricTooltipHooks.tagDeferred(draw));
    }
    ^///?}

    //? if >=26.3 {
    @Inject(method = "tooltip", at = @At("HEAD"))
    private void buildcraft$tooltipPre(Font font, List<ClientTooltipComponent> lines, int x, int y,
            ClientTooltipPositioner positioner, Identifier style, boolean extraSpace, CallbackInfo ci) {
        FabricTooltipHooks.firePre((GuiGraphicsExtractor) (Object) this, font, lines, x, y, positioner);
    }
    //?} elif >=26.1 {
    /^@Inject(method = "tooltip", at = @At("HEAD"))
    private void buildcraft$tooltipPre(Font font, List<ClientTooltipComponent> lines, int x, int y,
            ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        FabricTooltipHooks.firePre((GuiGraphicsExtractor) (Object) this, font, lines, x, y, positioner);
    }
    ^///?} elif >=1.21.10 {
    /^@Inject(method = "renderTooltip", at = @At("HEAD"))
    private void buildcraft$tooltipPre(Font font, List<ClientTooltipComponent> lines, int x, int y,
            ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        FabricTooltipHooks.firePre((GuiGraphics) (Object) this, font, lines, x, y, positioner);
    }
    ^///?} else {
    /^@Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void buildcraft$tooltipPre(Font font, List<ClientTooltipComponent> lines, int x, int y,
            ClientTooltipPositioner positioner, CallbackInfo ci) {
        FabricTooltipHooks.firePre((GuiGraphics) (Object) this, font, lines, x, y, positioner);
    }
    ^///?}
}
*///?}
