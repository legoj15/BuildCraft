/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot.pattern;

import buildcraft.api.filler.IFilledTemplate;
import buildcraft.api.filler.IFillerPatternShape;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.builders.BCBuildersSprites;

/** Clears the box and lays a floor along its bottom layer. 1.7.10's Flatten put the floor one layer below the box,
 * which a box-sized template cannot express; reaching outside the box is what left it "broken ATM" in 8.x. */
public class PatternFlatten extends Pattern implements IFillerPatternShape {
    public PatternFlatten() {
        super("flatten");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCBuildersSprites.FILLER_FLATTEN;
    }

    @Override
    public boolean fillTemplate(IFilledTemplate filledTemplate, IStatementParameter[] params) {
        filledTemplate.setPlaneXZ(0, true);
        return true;
    }
}
