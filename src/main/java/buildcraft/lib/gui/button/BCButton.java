/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

import buildcraft.lib.gui.BCGraphics;

/**
 * Base for BuildCraft custom buttons that draw a bespoke face.
 * <p>
 * The vanilla widget content-render hook diverges per MC line — 26.1 {@code extractContents},
 * 1.21.11 {@code renderContents}, 1.21.10 {@code renderWidget} — so this base owns that single
 * 3-way divergence and re-exposes it as the BC hook {@link #drawButtonContent}. Subclasses override
 * the hook (and call {@link #drawDefaultButtonSprite}/{@link #drawDefaultButtonLabel} for the stock
 * pieces) instead of repeating the directive in every button. This mirrors the {@code GuiBC8}
 * screen-hook refactor, one layer down.
 * <p>
 * The {@code MouseButtonEvent}-based click path ({@code mouseClicked}/{@code onClick}/{@code onPress})
 * is identical across all three lines, so click handling stays on the subclass with no directives.
 */
public abstract class BCButton extends AbstractButton {
    protected BCButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // The vanilla content-render override; just wraps the graphics and defers to the BC hook.
    //? if >=26.1 {
    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }
    //?} elif >=1.21.11 {
    /*@Override
    protected void renderContents(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }*/
    //?} else {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButtonContent(new BCGraphics(graphics), mouseX, mouseY, partialTick);
    }*/
    //?}

    /** Draw the button face. Default = the vanilla 9-sliced hover-aware sprite; override to customise. */
    protected void drawButtonContent(BCGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawDefaultButtonSprite(graphics);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** The vanilla 9-sliced, hover-aware button background sprite. */
    protected void drawDefaultButtonSprite(BCGraphics graphics) {
        //? if >=26.1 {
        extractDefaultSprite(graphics.raw);
        //?} elif >=1.21.11 {
        /*renderDefaultSprite(graphics.raw);*/
        //?} else {
        /*// 1.21.10 has no extract/renderDefaultSprite helper — replicate AbstractButton.renderWidget's blit.
        graphics.raw.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            SPRITES.get(this.active, this.isHoveredOrFocused()),
            getX(), getY(), getWidth(), getHeight(),
            net.minecraft.util.ARGB.white(this.alpha));*/
        //?}
    }

    /** The vanilla centred button label, at the widget's foreground colour. */
    protected void drawDefaultButtonLabel(BCGraphics graphics) {
        //? if >=26.1 {
        extractDefaultLabel(graphics.raw.textRendererForWidget(this,
            net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects.NONE));
        //?} elif >=1.21.11 {
        /*renderDefaultLabel(graphics.raw.textRendererForWidget(this,
            net.minecraft.client.gui.GuiGraphics.HoveredTextEffects.NONE));*/
        //?} else {
        /*renderString(graphics.raw, net.minecraft.client.Minecraft.getInstance().font,
            net.minecraft.util.ARGB.color(this.alpha, getFGColor()));*/
        //?}
    }
}
