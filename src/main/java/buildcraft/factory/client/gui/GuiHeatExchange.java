/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.EnumProgressState;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionEnd;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiHeatExchange extends GuiBC8<ContainerHeatExchange> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/heat_exchanger.png");
    private static final int SIZE_X = 176, SIZE_Y = 171;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    // Tank overlays drawn over the fluid colour. Sourced from the spare regions
    // below the main 176x171 GUI window.
    private static final GuiIcon OVERLAY_VERTICAL = new GuiIcon(TEXTURE, 0, 171, 16, 38);
    private static final GuiIcon OVERLAY_HORIZONTAL = new GuiIcon(TEXTURE, 17, 171, 34, 17);

    // Tank rectangles match the 1.12.2 layout. Modern multi-block mapping:
    //   END.tankInput    → top-left vertical    (1.12.2 inCoolable: hot fluid in)
    //   START.tankInput  → mid-left horizontal  (1.12.2 inHeatable: cold fluid in)
    //   END.tankOutput   → top-right horizontal (1.12.2 outHeated:  heated out)
    //   START.tankOutput → mid-right vertical   (1.12.2 outCooled:  cooled out)
    private static final int TANK_END_IN_X = 44, TANK_END_IN_Y = 12;
    private static final int TANK_END_IN_W = 16, TANK_END_IN_H = 38;

    private static final int TANK_START_IN_X = 44, TANK_START_IN_Y = 64;
    private static final int TANK_START_IN_W = 34, TANK_START_IN_H = 17;

    private static final int TANK_END_OUT_X = 98, TANK_END_OUT_Y = 12;
    private static final int TANK_END_OUT_W = 34, TANK_END_OUT_H = 17;

    private static final int TANK_START_OUT_X = 116, TANK_START_OUT_Y = 43;
    private static final int TANK_START_OUT_W = 16, TANK_START_OUT_H = 38;

    // Center wipe overlay: drawn at GUI (61,11) sized 54x71, sourced from texture (176,71).
    // The overlay represents fluid meeting in the middle of the multi-block. It wipes
    // in from the left during PREPARING, holds full during RUNNING, and clears from
    // the left during STOPPING (the visible region's left edge marches rightward).
    private static final int WIPE_SRC_X = 176, WIPE_SRC_Y = 71;
    private static final int WIPE_W = 54, WIPE_H = 71;
    private static final int WIPE_DST_X = 61, WIPE_DST_Y = 11;

    public GuiHeatExchange(ContainerHeatExchange menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        ExchangeSectionStart start = menu.startSection();
        ExchangeSectionEnd end = menu.endSection();

        if (start != null) {
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_START_IN_X, TANK_START_IN_Y, TANK_START_IN_W, TANK_START_IN_H)
                        .offset(mainGui.rootElement),
                start.tankInput,
                menu.widgetTankStartInput,
                OVERLAY_HORIZONTAL
            ));
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_START_OUT_X, TANK_START_OUT_Y, TANK_START_OUT_W, TANK_START_OUT_H)
                        .offset(mainGui.rootElement),
                start.tankOutput,
                menu.widgetTankStartOutput,
                OVERLAY_VERTICAL
            ));
            // START section: cold-in (heatable loop entry) and cooled-out (coolable loop exit).
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_START_IN_X, TANK_START_IN_Y, TANK_START_IN_W, TANK_START_IN_H)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.heat_exchange.cold_in.title", 0xFF_55_AA_FF,
                    "buildcraft.help.heat_exchange.cold_in.desc")));
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_START_OUT_X, TANK_START_OUT_Y, TANK_START_OUT_W, TANK_START_OUT_H)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.heat_exchange.cooled_out.title", 0xFF_AA_CC_FF,
                    "buildcraft.help.heat_exchange.cooled_out.desc")));
        }
        if (end != null) {
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_END_IN_X, TANK_END_IN_Y, TANK_END_IN_W, TANK_END_IN_H)
                        .offset(mainGui.rootElement),
                end.tankInput,
                menu.widgetTankEndInput,
                OVERLAY_VERTICAL
            ));
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_END_OUT_X, TANK_END_OUT_Y, TANK_END_OUT_W, TANK_END_OUT_H)
                        .offset(mainGui.rootElement),
                end.tankOutput,
                menu.widgetTankEndOutput,
                OVERLAY_HORIZONTAL
            ));
            // END section: hot-in (coolable loop entry) and heated-out (heatable loop exit).
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_END_IN_X, TANK_END_IN_Y, TANK_END_IN_W, TANK_END_IN_H)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.heat_exchange.hot_in.title", 0xFF_FF_55_55,
                    "buildcraft.help.heat_exchange.hot_in.desc")));
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_END_OUT_X, TANK_END_OUT_Y, TANK_END_OUT_W, TANK_END_OUT_H)
                        .offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.heat_exchange.heated_out.title", 0xFF_FF_AA_55,
                    "buildcraft.help.heat_exchange.heated_out.desc")));
        }
        // Centre wipe overlay — visualises the multi-block's PREPARING/RUNNING/STOPPING state.
        // The wipe pixels themselves draw at the full WIPE_W × WIPE_H rectangle above; the
        // help-hover rect is hand-tuned to hug the visible pipe junction in the middle of
        // the schematic, clear of every surrounding tank highlight.
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(73, 36, 30, 21).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.heat_exchange.progress.title", 0xFF_88_CC_88,
                "buildcraft.help.heat_exchange.progress.desc1",
                "buildcraft.help.heat_exchange.progress.desc2")));
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Intentionally no title or inventory label — the layout is too cramped
        // to fit either without overlapping the pipe schematic or the slots.
        super.extractLabels(graphics, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        drawCenterWipeOverlay(partialTicks);

        ExchangeSectionStart start = menu.startSection();
        ExchangeSectionEnd end = menu.endSection();
        if (start != null) {
            renderTankTooltip(graphics, mouseX, mouseY, start.tankInput,
                    TANK_START_IN_X, TANK_START_IN_Y, TANK_START_IN_W, TANK_START_IN_H);
            renderTankTooltip(graphics, mouseX, mouseY, start.tankOutput,
                    TANK_START_OUT_X, TANK_START_OUT_Y, TANK_START_OUT_W, TANK_START_OUT_H);
        }
        if (end != null) {
            renderTankTooltip(graphics, mouseX, mouseY, end.tankInput,
                    TANK_END_IN_X, TANK_END_IN_Y, TANK_END_IN_W, TANK_END_IN_H);
            renderTankTooltip(graphics, mouseX, mouseY, end.tankOutput,
                    TANK_END_OUT_X, TANK_END_OUT_Y, TANK_END_OUT_W, TANK_END_OUT_H);
        }
    }

    /**
     * Center processing overlay. Wipes in from left-to-right during PREPARING,
     * sits at full width during RUNNING, then clears from left-to-right during
     * STOPPING (the visible region's left edge moves right). Nothing is drawn
     * when OFF or when the structure is incomplete.
     */
    private void drawCenterWipeOverlay(float partialTicks) {
        ExchangeSectionStart start = menu.startSection();
        if (start == null) return;
        EnumProgressState state = start.getProgressState();
        if (state == EnumProgressState.OFF) return;

        double progress = Math.max(0.0, Math.min(1.0, start.getProgress(partialTicks)));
        int leftOffset;   // pixels of the source/destination shifted right (wipe-out)
        int visibleW;     // width of the visible slice
        if (state == EnumProgressState.PREPARING) {
            leftOffset = 0;
            visibleW = (int) Math.round(progress * WIPE_W);
        } else if (state == EnumProgressState.STOPPING) {
            leftOffset = (int) Math.round((1.0 - progress) * WIPE_W);
            visibleW = WIPE_W - leftOffset;
        } else {
            leftOffset = 0;
            visibleW = WIPE_W;
        }
        if (visibleW <= 0) return;

        int absX = leftPos + WIPE_DST_X + leftOffset;
        int absY = topPos + WIPE_DST_Y;
        new GuiIcon(TEXTURE, WIPE_SRC_X + leftOffset, WIPE_SRC_Y, visibleW, WIPE_H)
                .drawAt(absX, absY);
    }

    private void renderTankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            FluidStacksResourceHandler tank, int relX, int relY, int w, int h) {
        if (tank == null) return;
        int absX = leftPos + relX;
        int absY = topPos + relY;
        if (mouseX >= absX && mouseX < absX + w && mouseY >= absY && mouseY < absY + h) {
            int amount = (int) tank.getAmountAsLong(0);
            int capacity = (int) tank.getCapacityAsLong(0, FluidResource.EMPTY);

            List<Component> lines = new ArrayList<>();
            if (amount > 0) {
                lines.add(tank.getResource(0).toStack(amount).getHoverName());
            }
            lines.add(Component.literal(amount + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
            List<FormattedCharSequence> comps = new ArrayList<>();
            for (Component c : lines) {
                comps.add(c.getVisualOrderText());
            }
            graphics.setTooltipForNextFrame(font, comps, mouseX, mouseY);
        }
    }
}
