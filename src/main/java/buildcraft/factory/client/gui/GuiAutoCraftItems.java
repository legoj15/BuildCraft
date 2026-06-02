/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public class GuiAutoCraftItems extends GuiBC8<ContainerAutoCraftItems> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/autobench_item.png");
    private static final Identifier TEXTURE_MISC =
            Identifier.parse("buildcraftunofficial:textures/gui/misc_slots.png");
    private static final int SIZE_X = 176, SIZE_Y = 197;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE, SIZE_X, 0, 23, 10);
    private static final GuiIcon ICON_FILTER_OVERLAY_SAME = new GuiIcon(TEXTURE_MISC, 54, 0, 18, 18);
    private static final GuiIcon ICON_FILTER_OVERLAY_DIFFERENT = new GuiIcon(TEXTURE_MISC, 72, 0, 18, 18);

    private AWRecipeBookComponent recipeBookComponent;
    private ImageButton recipeBookButton;
    private boolean widthTooNarrow;

    public GuiAutoCraftItems(ContainerAutoCraftItems menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    // Vanilla's recipe book slides out from the left edge and would overlap a left-side
    // LedgerHelp, so we opt out via shouldAddHelpLedger() — matching 1.12.2.
    @Override
    protected boolean shouldAddHelpLedger() {
        return false;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true
            ));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;

        this.recipeBookComponent = new AWRecipeBookComponent(this.menu);
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow);

        // Shift GUI right when recipe book is open
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);

        // Add the recipe book toggle button
        ScreenPosition buttonPos = getRecipeBookButtonPosition();
        this.recipeBookButton = new ImageButton(
            buttonPos.x(), buttonPos.y(), 20, 18,
            RecipeBookComponent.RECIPE_BUTTON_SPRITES,
            p -> {
                this.recipeBookComponent.toggleVisibility();
                this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
                ScreenPosition newPos = getRecipeBookButtonPosition();
                this.recipeBookButton.setPosition(newPos.x(), newPos.y());
            }
        );
        addRenderableWidget(this.recipeBookButton);
        addRenderableWidget(this.recipeBookComponent);
    }

    private ScreenPosition getRecipeBookButtonPosition() {
        // Left of the 3x3 blueprint grid (starts at x=30), aligned with 2nd row
        return new ScreenPosition(this.leftPos + 5, this.topPos + 34);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.tick();
        }
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        // Draw crafting progress bar (arrow fill)
        if (menu.tile != null) {
            double progress = menu.tile.getProgress(0);
            if (progress > 0) {
                int progressWidth = (int) (ICON_PROGRESS.width * Math.min(progress, 1.0));
                if (progressWidth > 0) {
                    int px = 90 + (int) mainGui.rootElement.getX();
                    int py = 47 + (int) mainGui.rootElement.getY();
                    ICON_PROGRESS.drawCutInside(px, py, progressWidth, ICON_PROGRESS.height);
                }
            }
        }

        // Draw phantom filter items and colored overlays on material slots
        if (hasFilters()) {
            ItemHandlerSimple filters = menu.tile.invMaterialFilter;

            // First pass: render the phantom filter items inside material slots
            for (int s = 0; s < filters.getSlots(); s++) {
                ItemStack filterStack = filters.getStackInSlot(s);
                if (!filterStack.isEmpty()) {
                    SlotBase slot = menu.materialSlots[s];
                    int x = slot.x + (int) mainGui.rootElement.getX();
                    int y = slot.y + (int) mainGui.rootElement.getY();
                    graphics.fakeItem(filterStack, x, y); // null holder → clock/compass stay static
                    graphics.itemDecorations(this.font, filterStack, x, y, null);
                }
            }

            // Second pass: draw colored overlays on top
            for (int s = 0; s < filters.getSlots(); s++) {
                ItemStack filterStack = filters.getStackInSlot(s);
                if (!filterStack.isEmpty()) {
                    SlotBase slot = menu.materialSlots[s];
                    ItemStack real = slot.getItem();
                    GuiIcon icon;
                    if (real.isEmpty() || StackUtil.canMerge(real, filterStack)) {
                        icon = ICON_FILTER_OVERLAY_SAME;
                    } else {
                        icon = ICON_FILTER_OVERLAY_DIFFERENT;
                    }
                    int x = slot.x + (int) mainGui.rootElement.getX() - 1;
                    int y = slot.y + (int) mainGui.rootElement.getY() - 1;
                    icon.drawAt(x, y);
                }
            }
        }
    }

    private boolean hasFilters() {
        if (menu.tile == null) return false;
        ItemHandlerSimple filters = menu.tile.invMaterialFilter;
        for (int s = 0; s < filters.getSlots(); s++) {
            if (!filters.getStackInSlot(s).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawTooltipLayer(int mouseX, int mouseY, float partialTick) {
        // The panel itself renders via addRenderableWidget(recipeBookComponent) in init();
        // draw its tooltips on top here (mirrors vanilla AbstractRecipeBookScreen).
        if (this.recipeBookComponent != null && this.recipeBookComponent.isVisible()) {
            BCGraphics graphics = GuiIcon.getGuiGraphics();
            //? if >=26.1 {
            this.recipeBookComponent.extractTooltip(graphics.raw, mouseX, mouseY, this.hoveredSlot);
            //?} else {
            /*this.recipeBookComponent.renderTooltip(graphics.raw, mouseX, mouseY, this.hoveredSlot);*/
            //?}
        }
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean entered) {
        if (this.recipeBookComponent != null && this.recipeBookComponent.mouseClicked(event, entered)) {
            this.setFocused(this.recipeBookComponent);
            return true;
        }
        // Defer to super class to allow clicking slots even if recipe book logic is unfinished
        return super.mouseClicked(event, entered);
    }

    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean outside = mouseX < left || mouseY < top || mouseX >= left + this.imageWidth || mouseY >= top + this.imageHeight;
        return this.recipeBookComponent != null
            ? this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight) && outside
            : outside;
    }

    public void recipesUpdated() {
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.recipesUpdated();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.recipeBookComponent != null && this.recipeBookComponent.keyPressed(event)
            ? true : super.keyPressed(event);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    // No extractGhostRecipe() call: this machine fills real designated-material ghost
    // slots via AWRecipeBookComponent.fillGhostRecipe when a recipe is picked, so the
    // vanilla transient grid overlay does not apply.
}

