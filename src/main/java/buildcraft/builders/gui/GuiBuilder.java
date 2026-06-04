/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.gui;

import net.minecraft.ChatFormatting;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.button.BCButton;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.snapshot.EnumContainerContentsMode;
import buildcraft.builders.snapshot.EnumFluidHandlingMode;
import buildcraft.builders.tile.TileBuilder;

public class GuiBuilder extends GuiBC8<ContainerBuilder> {
    private static final Identifier TEXTURE_BASE =
            Identifier.parse("buildcraftunofficial:textures/gui/builder.png");
    private static final Identifier TEXTURE_BLUEPRINT =
            Identifier.parse("buildcraftunofficial:textures/gui/builder_blueprint.png");

    private static final int SIZE_X = 176;
    private static final int SIZE_BLUEPRINT_X = 256;
    private static final int SIZE_Y = 222;
    private static final int BLUEPRINT_WIDTH = 87;

    // Matches 1.12.2: overlay is the right-hand 87px of a 256-wide GUI, tank sprite is the
    // 16x47 strip at (0, 54) within builder_blueprint.png.
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_BLUEPRINT_GUI = new GuiIcon(
            TEXTURE_BLUEPRINT,
            SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH,
            0,
            BLUEPRINT_WIDTH,
            SIZE_Y
    );
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE_BLUEPRINT, 0, 54, 16, 47);

    // Tank rectangles in GUI-local coordinates (1.12.2 layout).
    private static final int TANK_Y = 145;
    private static final int TANK_W = 16;
    private static final int TANK_H = 47;
    private static int tankX(int i) {
        return 179 + i * 18;
    }

    // Fluid-mode cycling button — top-right of the main panel. 176px wide panel, button ends
    // at x=170 (clear of the right edge and above the first resource-slot row at y=72).
    private static final int FLUID_BUTTON_X = 150;
    private static final int FLUID_BUTTON_Y = 20;
    private static final int FLUID_BUTTON_SIZE = 20;

    // Container-contents-mode cycling button — sits immediately to the left of the fluid button
    // and uses the same widget sprite. Same y so the two buttons read as a row.
    private static final int CONTENTS_BUTTON_X = FLUID_BUTTON_X - FLUID_BUTTON_SIZE;
    private static final int CONTENTS_BUTTON_Y = FLUID_BUTTON_Y;
    private static final int CONTENTS_BUTTON_SIZE = FLUID_BUTTON_SIZE;

    // Help-ledger rectangles. Mirror ContainerBuilder's slot positions so the hover-highlight
    // covers the visible cells exactly (1.12.2 used the same coordinates).
    private static final int SNAPSHOT_X = 80, SNAPSHOT_Y = 27;
    private static final int RESOURCE_X = 8, RESOURCE_Y = 72;
    private static final int RESOURCE_W = 9 * 18 - 2, RESOURCE_H = 3 * 18 - 2;
    private static final int DISPLAY_X = 179, DISPLAY_Y = 18;
    private static final int DISPLAY_W = 4 * 18 - 2, DISPLAY_H = 6 * 18 - 2;
    private static final int TANK_ROW_X = 179;
    private static final int TANK_ROW_W = TileBuilder.TANK_COUNT * 18 - 2;

    private FluidModeButton fluidModeButton;
    private ContentsModeButton contentsModeButton;

    public GuiBuilder(ContainerBuilder container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_BLUEPRINT_X, SIZE_Y);
        // Player inventory is at y=140 (set in ContainerBuilder). The "Inventory" label goes
        // above it; imageHeight-94 puts it 11px above the first row, matching Minecraft chest.
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile == null) return;
        for (int i = 0; i < TileBuilder.TANK_COUNT; i++) {
            final int idx = i;
            mainGui.shownElements.add(new GuiElementFluidTank(
                    mainGui,
                    new GuiRectangle(tankX(idx), TANK_Y, TANK_W, TANK_H).offset(mainGui.rootElement),
                    menu.tile.getTank(idx),
                    idx < menu.widgetTanks.size() ? menu.widgetTanks.get(idx) : null,
                    ICON_TANK_OVERLAY
            ));
        }

        // The auto-attached LedgerHelp on the left walks gui.shownElements and collects each
        // element's ElementHelpInfo, so the DummyHelpElements below light up under the cursor
        // whether the ledger is open or closed.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(SNAPSHOT_X, SNAPSHOT_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.snapshot.title", 0xFF_88_CC_88,
                        "buildcraft.help.builder.snapshot.desc1",
                        "buildcraft.help.builder.snapshot.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(RESOURCE_X, RESOURCE_Y, RESOURCE_W, RESOURCE_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.resources.title", 0xFF_FF_CC_88,
                        "buildcraft.help.builder.resources.desc1",
                        "buildcraft.help.builder.resources.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(FLUID_BUTTON_X, FLUID_BUTTON_Y, FLUID_BUTTON_SIZE, FLUID_BUTTON_SIZE)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.fluid_mode.title", 0xFF_88_CC_FF,
                        "buildcraft.help.builder.fluid_mode.desc1",
                        "buildcraft.help.builder.fluid_mode.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(CONTENTS_BUTTON_X, CONTENTS_BUTTON_Y, CONTENTS_BUTTON_SIZE, CONTENTS_BUTTON_SIZE)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.contents_mode.title", 0xFF_CC_88_CC,
                        "buildcraft.help.builder.contents_mode.desc1",
                        "buildcraft.help.builder.contents_mode.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(DISPLAY_X, DISPLAY_Y, DISPLAY_W, DISPLAY_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.display.title", 0xFF_E1_C9_2F,
                        "buildcraft.help.builder.display.desc1",
                        "buildcraft.help.builder.display.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_ROW_X, TANK_Y, TANK_ROW_W, TANK_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.builder.tanks.title", 0xFF_88_AA_FF,
                        "buildcraft.help.builder.tanks.desc1",
                        "buildcraft.help.builder.tanks.desc2")));
    }

    @Override
    protected void init() {
        super.init();
        fluidModeButton = new FluidModeButton(leftPos + FLUID_BUTTON_X, topPos + FLUID_BUTTON_Y);
        addRenderableWidget(fluidModeButton);
        contentsModeButton = new ContentsModeButton(leftPos + CONTENTS_BUTTON_X, topPos + CONTENTS_BUTTON_Y);
        addRenderableWidget(contentsModeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (fluidModeButton != null) {
            fluidModeButton.refreshTooltip();
        }
        if (contentsModeButton != null) {
            contentsModeButton.refreshTooltip();
        }
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        // Base GUI spans the left 176px; the blueprint overlay fills the right 87px. We always
        // draw both — the overlay carries the tank gauge art and display-slot frames, which
        // should be visible even before a blueprint is loaded so the player can see where
        // they'll appear (1.12.2 drew it unconditionally too).
        ICON_GUI.drawAt(mainGui.rootElement);
        ICON_BLUEPRINT_GUI.drawAt(mainGui.rootElement.offset(SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, 0));
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();

        // Title — centered over the main section.
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        graphics.text(font, titleStr, (SIZE_X - titleWidth) / 2, 6, 0xFF404040, false);

        // Progress readout — visible only while something is actively being broken/placed.
        int leftToBreak = menu.getSyncedLeftToBreak();
        int leftToPlace = menu.getSyncedLeftToPlace();
        int y = 50;
        if (leftToBreak > 0) {
            graphics.text(font,
                    Component.literal("Break: " + leftToBreak).withStyle(ChatFormatting.DARK_GRAY),
                    10, y, 0xFF404040, false);
            y += 10;
        }
        if (leftToPlace > 0) {
            graphics.text(font,
                    Component.literal("Place: " + leftToPlace).withStyle(ChatFormatting.DARK_GRAY),
                    10, y, 0xFF404040, false);
        }
    }


    /**
     * 20×20 button that cycles the Builder's {@link EnumFluidHandlingMode} each click. The icon
     * (barrier/bricks/bucket) and tooltip reflect the current server-synced mode, so there's no
     * local optimistic state to reconcile — the button redraws whenever
     * {@code ContainerBuilder}'s ContainerData pushes a new ordinal.
     */
    private class FluidModeButton extends BCButton {
        private EnumFluidHandlingMode lastKnown;

        FluidModeButton(int x, int y) {
            super(x, y, FLUID_BUTTON_SIZE, FLUID_BUTTON_SIZE, Component.empty());
            refreshTooltip();
        }

        @Override
        public void onPress(InputWithModifiers modifiers) {
            menu.sendMessage(ContainerBuilder.NET_FLUID_MODE_CLICK, buf -> {});
        }

        @Override
        protected void drawButtonContent(BCGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Vanilla 9-sliced, hover-aware button sprite — consistent with stock Button + resource packs.
            drawDefaultButtonSprite(graphics);
            EnumFluidHandlingMode mode = menu.getSyncedFluidMode();
            graphics.item(mode.icon(), getX() + 2, getY() + 2);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        void refreshTooltip() {
            EnumFluidHandlingMode mode = menu.getSyncedFluidMode();
            if (mode == lastKnown) return;
            lastKnown = mode;
            setTooltip(Tooltip.create(Component.translatable(mode.tooltipKey())));
        }
    }

    /**
     * 20×20 button that toggles whether the Builder fills placed containers (chests/hoppers/
     * barrels/shulkers/the furnace family/dispensers/droppers/brewing stands) with their
     * captured contents. Renders a vanilla chest as the base icon so the toggle adapts to
     * resource packs; under {@link EnumContainerContentsMode#IGNORE} also overlays the vanilla
     * barrier item (the universal "no" sprite) so the state reads at a glance.
     */
    private class ContentsModeButton extends BCButton {
        private static final net.minecraft.world.item.ItemStack CHEST_ICON =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST);
        private static final net.minecraft.world.item.ItemStack BARRIER_OVERLAY =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER);

        private EnumContainerContentsMode lastKnown;

        ContentsModeButton(int x, int y) {
            super(x, y, CONTENTS_BUTTON_SIZE, CONTENTS_BUTTON_SIZE, Component.empty());
            refreshTooltip();
        }

        @Override
        public void onPress(InputWithModifiers modifiers) {
            menu.sendMessage(ContainerBuilder.NET_CONTENTS_MODE_CLICK, buf -> {});
        }

        @Override
        protected void drawButtonContent(BCGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawDefaultButtonSprite(graphics);
            graphics.item(CHEST_ICON, getX() + 2, getY() + 2);
            if (menu.getSyncedContentsMode() == EnumContainerContentsMode.IGNORE) {
                // Both items at the same origin; the barrier's transparent pixels let the chest show through.
                graphics.item(BARRIER_OVERLAY, getX() + 2, getY() + 2);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        void refreshTooltip() {
            EnumContainerContentsMode mode = menu.getSyncedContentsMode();
            if (mode == lastKnown) return;
            lastKnown = mode;
            setTooltip(Tooltip.create(Component.translatable(mode.tooltipKey())));
        }
    }
}
