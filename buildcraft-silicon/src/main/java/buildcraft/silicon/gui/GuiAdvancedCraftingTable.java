/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

public class GuiAdvancedCraftingTable extends GuiBC8<ContainerAdvancedCraftingTable> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftsilicon:textures/gui/advanced_crafting_table.png");
    private static final int SIZE_X = 176, SIZE_Y = 241;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE_BASE, SIZE_X, 0, 4, 70);
    private static final GuiRectangle RECT_PROGRESS = new GuiRectangle(164, 7, 4, 70);

    private ACTRecipeBookComponent recipeBookComponent;
    private ImageButton recipeBookButton;
    private boolean widthTooNarrow;

    public GuiAdvancedCraftingTable(ContainerAdvancedCraftingTable container, Inventory playerInventory,
        Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        mainGui.shownElements.add(new LedgerTablePower(mainGui, menu.tile, true));
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;

        this.recipeBookComponent = new ACTRecipeBookComponent(this.menu);
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow);

        // Compute position — shift right when recipe book is open
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);

        // Add the recipe book toggle button
        ScreenPosition buttonPos = getRecipeBookButtonPosition();
        this.recipeBookButton = new ImageButton(
            buttonPos.x(), buttonPos.y(), 20, 18,
            RecipeBookComponent.RECIPE_BUTTON_SPRITES,
            p -> {
                this.recipeBookComponent.toggleVisibility();
                this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
                // Update button position
                ScreenPosition newPos = getRecipeBookButtonPosition();
                this.recipeBookButton.setPosition(newPos.x(), newPos.y());
            }
        );
        addRenderableWidget(this.recipeBookButton);
        addRenderableWidget(this.recipeBookComponent);
    }

    private ScreenPosition getRecipeBookButtonPosition() {
        // Left of the 3x3 blueprint grid (starts at x=33), aligned with 2nd row (y=34)
        return new ScreenPosition(this.leftPos + 7, this.topPos + 33);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.tick();
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        long target = menu.tile.getTarget();
        if (target != 0) {
            double v = (double) menu.tile.power / target;
            ICON_PROGRESS.drawCutInside(
                new GuiRectangle(
                    RECT_PROGRESS.x,
                    (int) (RECT_PROGRESS.y + RECT_PROGRESS.height * Math.max(1 - v, 0)),
                    RECT_PROGRESS.width,
                    (int) Math.ceil(RECT_PROGRESS.height * Math.min(v, 1))
                ).offset(mainGui.rootElement)
            );
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.recipeBookComponent != null && this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.extractContents(graphics, mouseX, mouseY, partialTick);
            // MC 26.1: RecipeBookComponent rendering APIs changed significantly
            // TODO: Implement proper recipe book rendering with new MC 26.1 API
        } else {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            // MC 26.1: RecipeBookComponent rendering APIs changed significantly
            // TODO: Implement proper recipe book rendering with new MC 26.1 API
        }
        // MC 26.1: Tooltip APIs changed significantly
        // TODO: Implement proper recipe book tooltip rendering with new MC 26.1 API
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        mainGui.drawBackgroundLayer(partialTicks, mouseX, mouseY, () -> {
            drawBackgroundTexture(graphics);
        });
        mainGui.drawElementBackgrounds();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String title = I18n.get("block.buildcraftsilicon.advanced_crafting_table");
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 5, 0xFF404040, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean entered) {
        if (this.recipeBookComponent != null && this.recipeBookComponent.mouseClicked(event, entered)) {
            this.setFocused(this.recipeBookComponent);
            return true;
        }
        return this.widthTooNarrow && this.recipeBookComponent != null && this.recipeBookComponent.isVisible()
            ? true : super.mouseClicked(event, entered);
    }


    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean outside = mouseX < left || mouseY < top || mouseX >= left + this.imageWidth || mouseY >= top + this.imageHeight;
        return this.recipeBookComponent != null
            ? this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight) && outside
            : outside;
    }

    // MC 26.1: slotClicked signature changed (ClickAction→ContainerInput)
    // Recipe book slot tracking is non-critical — stubbed for now.
    // TODO: Implement proper recipe book integration with new MC 26.1 API.


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


    // MC 26.1: renderGhostRecipe removed from RecipeBookComponent API.
    // TODO: Implement proper ghost recipe rendering with new MC 26.1 API.
}
