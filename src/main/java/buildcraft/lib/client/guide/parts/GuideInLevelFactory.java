/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.world.LevelInfo;

/** Factory for creating GuideInLevel instances.
 * Stubbed — returns null until 3D rendering is implemented. */
public class GuideInLevelFactory implements GuidePartFactory {
    private final LevelInfo info;

    public GuideInLevelFactory(LevelInfo info) {
        this.info = info;
    }

    @Override
    public GuideInLevel createNew(GuiGuide gui) {
        // Stubbed — 3D in-world rendering not yet implemented
        return null;
    }
}
