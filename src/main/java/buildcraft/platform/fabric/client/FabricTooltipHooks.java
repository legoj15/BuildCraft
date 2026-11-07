/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.client.BCTooltipPreContext;

/^*
 * NeoForge's {@code RenderTooltipEvent.Pre} on Fabric ({@link FabricClientHooks#onTooltipPre}), fed by the client
 * mixins {@code GuiGraphicsExtractorMixin} and {@code AbstractContainerScreenMixin}. It fires where NeoForge's does: at
 * the head of {@link GuiGraphicsExtractor#tooltip}, before the positioner runs.
 *
 * <p>Vanilla's tooltip draw carries no item stack (NeoForge patches one through), so the stack takes a side channel:
 * the item tooltip requests ({@link GuiGraphicsExtractor#setTooltipForNextFrame(Font, ItemStack, int, int)} and a
 * container screen's hovered slot) name it with {@link #describing}, the deferred draw queued during that request is
 * tagged with it ({@link #tagDeferred}), and that draw hands it to the listeners. Any other tooltip reaches them with
 * an empty stack, as on NeoForge. Render thread only.
 *^/
public final class FabricTooltipHooks {

    private static final List<Consumer<BCTooltipPreContext>> PRE = new CopyOnWriteArrayList<>();

    /^* The stack the tooltip being requested right now describes, or empty. *^/
    private static ItemStack requestStack = ItemStack.EMPTY;
    /^* The stack the tooltip being drawn right now describes, or empty. *^/
    private static ItemStack drawStack = ItemStack.EMPTY;

    private FabricTooltipHooks() {}

    static void onPre(Consumer<BCTooltipPreContext> listener) {
        PRE.add(listener);
    }

    /^* Runs a tooltip request that describes {@code stack}. *^/
    public static void describing(ItemStack stack, Runnable request) {
        ItemStack outer = requestStack;
        requestStack = stack;
        try {
            request.run();
        } finally {
            requestStack = outer;
        }
    }

    /^* The deferred tooltip draw being queued, carrying the stack of the request that queues it (if any). *^/
    public static Runnable tagDeferred(Runnable draw) {
        ItemStack stack = requestStack;
        if (stack.isEmpty()) {
            return draw;
        }
        return () -> {
            ItemStack outer = drawStack;
            drawStack = stack;
            try {
                draw.run();
            } finally {
                drawStack = outer;
            }
        };
    }

    /^* A tooltip is about to be drawn; {@code x}/{@code y} are the positioner's inputs. *^/
    public static void firePre(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> components,
            int x, int y, ClientTooltipPositioner positioner) {
        if (PRE.isEmpty()) {
            return;
        }
        BCTooltipPreContext context = new Context(drawStack, graphics, x, y, graphics.guiWidth(), graphics.guiHeight(),
                font, components, positioner);
        for (Consumer<BCTooltipPreContext> listener : PRE) {
            listener.accept(context);
        }
    }

    private record Context(ItemStack stack, GuiGraphicsExtractor graphics, int x, int y, int screenWidth,
            int screenHeight, Font font, List<ClientTooltipComponent> components, ClientTooltipPositioner positioner)
            implements BCTooltipPreContext {}
}
*///?}
