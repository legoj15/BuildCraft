/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.slot.IPhantomSlot;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;

/**
 * REI integration plugin for BuildCraft Silicon.
 * Registers the Advanced Crafting Table with recipe transfer, click area,
 * catalyst, and ghost drag-and-drop support.
 */
@REIPluginClient
public class BCSiliconReiPlugin implements REIClientPlugin {

    private static final CategoryIdentifier<?> CRAFTING =
            CategoryIdentifier.of("minecraft", "plugins/crafting");

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(context -> {
            var containerScreen = context.getContainerScreen();
            if (!(containerScreen instanceof GuiAdvancedCraftingTable gui)) {
                return TransferHandler.Result.createNotApplicable();
            }

            Display display = context.getDisplay();
            if (!display.getCategoryIdentifier().equals(CRAFTING)) {
                return TransferHandler.Result.createNotApplicable();
            }

            if (!context.isActuallyCrafting()) {
                return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
            }

            if (display.getDisplayLocation().isPresent()) {
                String recipeIdStr = display.getDisplayLocation().get().toString();
                ContainerAdvancedCraftingTable container = gui.getMenu();
                container.sendMessage(ContainerBC_Neptune.NET_JEI_RECIPE_TRANSFER, buf -> {
                    buf.writeUtf(recipeIdStr);
                });
            }

            return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
        });
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        // Click the progress arrow between the blueprint grid and the output
        // slot to view crafting recipes.
        registry.registerClickArea(
                screen -> new Rectangle(
                        screen.getLeftPos() + 93,
                        screen.getTopPos() + 32,
                        23, 16),
                GuiAdvancedCraftingTable.class,
                CRAFTING);

        // Ghost drag-and-drop onto phantom slots
        registry.registerDraggableStackVisitor(new PhantomSlotDragVisitor());
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.addWorkstations(CRAFTING, EntryStacks.of(BCSiliconItems.ADVANCED_CRAFTING_TABLE.get()));
    }

    /** Handles drag-and-drop from REI's ingredient list onto phantom slots. */
    private static class PhantomSlotDragVisitor
            implements DraggableStackVisitor<GuiAdvancedCraftingTable> {

        @Override
        public <R extends Screen> boolean isHandingScreen(R screen) {
            return screen instanceof GuiAdvancedCraftingTable;
        }

        @Override
        public Stream<BoundsProvider> getDraggableAcceptingBounds(
                DraggingContext<GuiAdvancedCraftingTable> context, DraggableStack stack) {
            GuiAdvancedCraftingTable gui = context.getScreen();
            ContainerBC_Neptune container = gui.getMenu();
            List<BoundsProvider> targets = new ArrayList<>();

            for (int i = 0; i < container.slots.size(); i++) {
                Slot slot = container.slots.get(i);
                if (slot instanceof IPhantomSlot) {
                    int x = gui.getLeftPos() + slot.x;
                    int y = gui.getTopPos() + slot.y;
                    targets.add(BoundsProvider.ofRectangle(new Rectangle(x, y, 16, 16)));
                }
            }
            return targets.stream();
        }

        @Override
        public DraggedAcceptorResult acceptDraggedStack(
                DraggingContext<GuiAdvancedCraftingTable> context, DraggableStack stack) {
            GuiAdvancedCraftingTable gui = context.getScreen();
            ContainerBC_Neptune container = gui.getMenu();

            Object value = stack.getStack().getValue();
            if (!(value instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                return DraggedAcceptorResult.PASS;
            }

            double mouseX = context.getCurrentPosition().x;
            double mouseY = context.getCurrentPosition().y;

            for (int i = 0; i < container.slots.size(); i++) {
                Slot slot = container.slots.get(i);
                if (slot instanceof IPhantomSlot) {
                    int x = gui.getLeftPos() + slot.x;
                    int y = gui.getTopPos() + slot.y;
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(itemStack.getItem()).toString();
                        final int slotIdx = i;
                        container.sendMessage(ContainerBC_Neptune.NET_GHOST_SLOT_SET, buf -> {
                            buf.writeShort(slotIdx);
                            buf.writeUtf(itemId);
                        });
                        return DraggedAcceptorResult.ACCEPTED;
                    }
                }
            }

            return DraggedAcceptorResult.PASS;
        }
    }
}
