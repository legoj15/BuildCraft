/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.rei;

import java.util.ArrayList;
import java.util.Collection;

import me.shedaniel.math.Rectangle;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

/**
 * REI client-side integration plugin for BuildCraft.
 * Registers ledger bounding boxes as exclusion zones so REI's
 * overlay panel is pushed out of the way when ledgers are open.
 */
@REIPluginClient
public class BCCoreReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(GuiBC8.class, screen -> {
            Collection<Rectangle> areas = new ArrayList<>();
            for (IGuiElement element : screen.mainGui.shownElements) {
                if (element instanceof Ledger_Neptune ledger) {
                    int x = (int) ledger.getX();
                    int y = (int) ledger.getY();
                    int w = (int) Math.ceil(ledger.getWidth());
                    int h = (int) Math.ceil(ledger.getHeight());
                    if (w > 0 && h > 0) {
                        areas.add(new Rectangle(x, y, w, h));
                    }
                }
            }
            return areas;
        });
    }
}
