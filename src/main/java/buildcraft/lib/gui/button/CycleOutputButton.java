/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import buildcraft.lib.gui.BCGraphics;

/**
 * An icon button — a vanilla Minecraft button backdrop with a centred 12×12 {@code cycle.png} — that
 * cycles a machine's selected crafting output. The backdrop greys itself through the vanilla
 * disabled-button sprite when {@link #active} is false (i.e. the grid has no alternative outputs to
 * cycle to), so no manual tint is needed. Used by the Auto Workbench and Advanced Crafting Table for
 * the conflicting-recipe cycle feature (the issue #20 follow-up).
 *
 * <p>The owning screen sets {@link #active} and the tooltip from the synced match count each tick;
 * the click sends the cycle message (the server is a no-op past a single match).
 */
public class CycleOutputButton extends BCButton {
    // Lives under textures/gui/ (a direct-blit GUI texture), NOT textures/icons/ — the latter is
    // swept wholesale onto the blocks atlas by assets/minecraft/atlases/blocks.json's "icons/"
    // source, and a non-power-of-two icon there caps the whole atlas's mip level (see issue notes).
    private static final Identifier ICON =
        Identifier.parse("buildcraftunofficial:textures/gui/cycle.png");
    private static final int ICON_SIZE = 10;

    private final Runnable onCycle;

    public CycleOutputButton(int x, int y, Runnable onCycle) {
        super(x, y, 14, 14, Component.empty());
        this.onCycle = onCycle;
    }

    // The AbstractButton click hook gained an InputWithModifiers arg in 1.21.10.
    //? if >=1.21.10 {
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) {
        onCycle.run();
    }
    //?} else {
    /*@Override
    public void onPress() {
        onCycle.run();
    }*/
    //?}

    @Override
    protected void drawButtonContent(BCGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Normal Minecraft button backdrop — hover-aware, and greyed via the vanilla disabled
        // sprite when !active (no manual tint needed).
        drawDefaultButtonSprite(graphics);
        // Centre the 12×12 icon on the button face.
        int iconX = getX() + (getWidth() - ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2;
        //? if >=1.21.10 {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON,
            iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        //?} else {
        /*graphics.blit(ICON, iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);*/
        //?}
    }
}
