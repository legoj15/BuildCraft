/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.util.EntryStacks;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import buildcraft.lib.gui.BCContainer;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.slot.IPhantomSlot;

/**
 * Shared REI plumbing for BuildCraft's two phantom-grid crafting tables (Auto Workbench, Advanced
 * Crafting Table). Both register the same four things — a "+" recipe-transfer handler that forwards
 * the chosen recipe's id to the server, a click area over the progress arrow, ghost drag-and-drop onto
 * the phantom slots, and a workstation catalyst — differing only in the screen/menu type, the
 * workstation item, and the click-area rectangle. Each subsystem's {@code REIClientPlugin} holds one of
 * these and delegates its three register callbacks here.
 *
 * <p><b>NOTE:</b> the whole {@code compat/rei} tree is currently compile-excluded (no MC 26.1-compatible
 * REI build yet — see the {@code exclude("**}{@code /compat/rei/**")} in build.gradle.kts and the
 * commented-out REI dependency), so this class is <i>not</i> built by the compile/test gate. Keep it in
 * sync with the REI + Minecraft APIs by hand until REI is re-enabled.
 *
 * @param <G> the screen type (a {@link GuiBC8})
 * @param <M> its menu type
 */
public final class ReiCraftingTableSupport<G extends GuiBC8<M>, M extends AbstractContainerMenu & BCContainer> {

    private static final CategoryIdentifier<?> CRAFTING = CategoryIdentifier.of("minecraft", "plugins/crafting");

    private final Class<G> guiClass;
    private final Supplier<? extends ItemLike> workstation;
    private final int clickX;
    private final int clickY;
    private final int clickW;
    private final int clickH;

    /**
     * @param guiClass    the screen class this plugin handles.
     * @param workstation the block/item that hosts the recipes (the REI catalyst / workstation).
     * @param clickX      click-area rectangle, relative to the screen's top-left ({@code getGuiLeft()}/
     * @param clickY      {@code getGuiTop()}): x-offset, y-offset, width, height. Clicking it opens the
     * @param clickW      crafting recipe category.
     * @param clickH      —
     */
    public ReiCraftingTableSupport(Class<G> guiClass, Supplier<? extends ItemLike> workstation,
            int clickX, int clickY, int clickW, int clickH) {
        this.guiClass = guiClass;
        this.workstation = workstation;
        this.clickX = clickX;
        this.clickY = clickY;
        this.clickW = clickW;
        this.clickH = clickH;
    }

    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(context -> {
            var containerScreen = context.getContainerScreen();
            if (!guiClass.isInstance(containerScreen)) {
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
                M container = guiClass.cast(containerScreen).getMenu();
                container.sendMessage(ContainerBC_Neptune.NET_JEI_RECIPE_TRANSFER, buf -> buf.writeUtf(recipeIdStr));
            }

            return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
        });
    }

    public void registerScreens(ScreenRegistry registry) {
        registry.registerClickArea(
                screen -> new Rectangle(screen.getGuiLeft() + clickX, screen.getGuiTop() + clickY, clickW, clickH),
                guiClass,
                CRAFTING);
        registry.registerDraggableStackVisitor(new PhantomSlotDragVisitor<>(guiClass));
    }

    public void registerCategories(CategoryRegistry registry) {
        registry.addWorkstations(CRAFTING, EntryStacks.of(workstation.get()));
    }

    /** Handles drag-and-drop from REI's ingredient list onto a BuildCraft table's phantom slots. */
    private static class PhantomSlotDragVisitor<G extends GuiBC8<M>, M extends AbstractContainerMenu & BCContainer>
            implements DraggableStackVisitor<G> {

        private final Class<G> guiClass;

        PhantomSlotDragVisitor(Class<G> guiClass) {
            this.guiClass = guiClass;
        }

        @Override
        public <R extends Screen> boolean isHandingScreen(R screen) {
            return guiClass.isInstance(screen);
        }

        @Override
        public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<G> context, DraggableStack stack) {
            G gui = context.getScreen();
            M container = gui.getMenu();
            List<BoundsProvider> targets = new ArrayList<>();

            for (int i = 0; i < container.slots.size(); i++) {
                Slot slot = container.slots.get(i);
                if (slot instanceof IPhantomSlot) {
                    int x = gui.getGuiLeft() + slot.x;
                    int y = gui.getGuiTop() + slot.y;
                    targets.add(BoundsProvider.ofRectangle(new Rectangle(x, y, 16, 16)));
                }
            }
            return targets.stream();
        }

        @Override
        public DraggedAcceptorResult acceptDraggedStack(DraggingContext<G> context, DraggableStack stack) {
            G gui = context.getScreen();
            M container = gui.getMenu();

            Object value = stack.getStack().getValue();
            if (!(value instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                return DraggedAcceptorResult.PASS;
            }

            double mouseX = context.getCurrentPosition().x;
            double mouseY = context.getCurrentPosition().y;

            for (int i = 0; i < container.slots.size(); i++) {
                Slot slot = container.slots.get(i);
                if (slot instanceof IPhantomSlot) {
                    int x = gui.getGuiLeft() + slot.x;
                    int y = gui.getGuiTop() + slot.y;
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
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
