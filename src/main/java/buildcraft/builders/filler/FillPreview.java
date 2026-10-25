/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.filler;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.BlockUtil;

import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.TemplateBuilder;

/**
 * Which cells a Filler's pattern still has to fill, for the preview its block entity renderer draws. Common-side on
 * purpose (positions only, nothing to render), so the cell computation is unit-testable; the drawing is
 * {@code FillPreviewRenderer}.
 */
public final class FillPreview {
    private FillPreview() {}

    /**
     * Every cell the template marks, as a world position, that {@code wanted} accepts. Walks the template's set bits
     * rather than every cell of the box, so a hollow pattern over a big box costs what the pattern holds.
     */
    public static List<BlockPos> collect(Template.BuildingInfo info, Predicate<BlockPos> wanted) {
        Template template = info.getSnapshot();
        BitSet data = template.data;
        List<BlockPos> cells = new ArrayList<>();
        for (int i = data.nextSetBit(0); i >= 0; i = data.nextSetBit(i + 1)) {
            BlockPos pos = info.toWorld(template.indexToPos(i));
            if (wanted.test(pos)) {
                cells.add(pos);
            }
        }
        return cells;
    }

    /**
     * The cells the pattern would fill that the Filler could place a block into right now: the same test the Filler's
     * own placement uses ({@link TemplateBuilder#isFillableSlot}), so a cell holding replaceable grass still shows and
     * a cell already built does not. Cells in chunks this client has not loaded are left out, or they would all read as
     * empty air.
     */
    public static List<BlockPos> collectFillable(Level level, Template.BuildingInfo info) {
        return collect(info, pos -> BlockUtil.isChunkPresent(level, pos) && TemplateBuilder.isFillableSlot(level, pos));
    }
}
