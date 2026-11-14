/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.robotics.container.ContainerRequester;

/** Ported from 7.1.x {@code GuiRequester} against the 8.0.x-era GUI texture (196×181 base): the request
 *  template grid on the left (the container's phantom slots — click with N held to request N), the
 *  delivery slots on the right, the player inventory below. Like 7.1.x, this draws no text of its own —
 *  everything visible is a slot, so the stock rendering does all the work. */
public class GuiRequester extends GuiBC8<ContainerRequester> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/requester.png");
    private static final int SIZE_X = 196, SIZE_Y = 181;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public GuiRequester(ContainerRequester container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                    () -> menu.tile != null ? menu.tile.getOwner() : null, true));
        }

        // Help-ledger entries, one per slot grid — see GuiZonePlanner / GuiProgrammingTable for the same
        // pattern. The rectangles cover the 4×5 phantom template grid and the 4×5 delivery grid beside it,
        // drawn on the slot frames (each slot's frame starts 1 px before ContainerRequester's slot position), so the
        // ledger can highlight each grid as a whole.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(8, 6, 72, 90).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.requester.requests.title", 0xFF_88_CC_FF,
                        "buildcraft.help.requester.requests.desc1",
                        "buildcraft.help.requester.requests.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(116, 6, 72, 90).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.requester.delivery.title", 0xFF_88_CC_88,
                        "buildcraft.help.requester.delivery.desc1",
                        "buildcraft.help.requester.delivery.desc2")));
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }
}
