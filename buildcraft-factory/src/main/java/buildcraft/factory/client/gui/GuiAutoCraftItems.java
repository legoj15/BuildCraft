/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.ledger.LedgerOwnership;

public class GuiAutoCraftItems extends GuiBC8<ContainerAutoCraftItems> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftfactory:textures/gui/autobench_item.png");
    private static final int SIZE_X = 176, SIZE_Y = 197;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE, SIZE_X, 0, 23, 10);
    private static final buildcraft.lib.gui.pos.GuiRectangle RECT_PROGRESS =
            new buildcraft.lib.gui.pos.GuiRectangle(90, 47, 23, 10);

    private AWRecipeBookComponent recipeBookComponent;
    private ImageButton recipeBookButton;
    private boolean widthTooNarrow;

    public GuiAutoCraftItems(ContainerAutoCraftItems menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
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
        return new ScreenPosition(this.leftPos + 5, this.topPos + 33);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.tick();
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.recipeBookComponent != null && this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.renderBg(graphics, partialTick, mouseX, mouseY);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
            if (this.recipeBookComponent != null) {
                this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.renderTooltip(graphics, mouseX, mouseY, null);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        GuiIcon.setGuiGraphics(graphics);
        mainGui.drawBackgroundLayer(partialTicks, mouseX, mouseY, () -> {
            drawBackgroundTexture(graphics);
        });
        mainGui.drawElementBackgrounds();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No labels — matches 1.12.2 which only has the GUI texture
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

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.slotClicked(slot);
        }
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

    public void renderGhostRecipe(GuiGraphics graphics, boolean p_283495_) {
        if (this.recipeBookComponent != null) {
            this.recipeBookComponent.renderGhostRecipe(graphics, p_283495_);
        }
    }
}

