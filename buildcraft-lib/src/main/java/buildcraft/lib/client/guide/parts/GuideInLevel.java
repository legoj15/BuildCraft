/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.world.LevelState;

/** Renders a 3D in-world scene within the guide book.
 * Stubbed — 3D rendering in guide pages requires a full rendering pipeline. */
public class GuideInLevel extends GuidePart {
    private boolean fullscreen = false;
    private final LevelState state;

    public GuideInLevel(GuiGuide gui, boolean fullscreen, LevelState state) {
        super(gui);
        this.fullscreen = fullscreen;
        this.state = state;
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        // Stubbed — 3D in-world rendering not yet implemented
        return current;
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        // Stubbed — 3D in-world interaction not yet implemented
        return current;
    }
}
