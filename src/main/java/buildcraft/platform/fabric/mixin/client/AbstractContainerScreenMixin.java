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

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/^import net.minecraft.client.gui.GuiGraphics;
^///?}
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import buildcraft.platform.fabric.client.FabricTooltipHooks;

/^*
 * A container screen's hovered-slot tooltip names its stack to {@link FabricTooltipHooks}: vanilla requests it with
 * the lines alone (NeoForge's patch passes the stack), so without this a slot tooltip would reach the pre listeners
 * with an empty stack.
 *^/
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    //? if >=26.3 {
    @WrapOperation(method = "extractTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V"))
    private void buildcraft$describeSlotTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, boolean extraSpace,
            Operation<Void> original) {
        FabricTooltipHooks.describing(hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem(),
                () -> original.call(graphics, font, lines, image, x, y, style, extraSpace));
    }
    //?} elif >=26.1 {
    /^// Before 26.3 the tooltip request has no extra-space flag.
    @WrapOperation(method = "extractTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
    private void buildcraft$describeSlotTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, Operation<Void> original) {
        FabricTooltipHooks.describing(hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem(),
                () -> original.call(graphics, font, lines, image, x, y, style));
    }
    ^///?} else {
    /^// Before 26.1 the screen's tooltip step is renderTooltip, on GuiGraphics.
    @WrapOperation(method = "renderTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
    private void buildcraft$describeSlotTooltip(GuiGraphics graphics, Font font, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y, Identifier style, Operation<Void> original) {
        FabricTooltipHooks.describing(hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem(),
                () -> original.call(graphics, font, lines, image, x, y, style));
    }
    ^///?}
}
*///?}
