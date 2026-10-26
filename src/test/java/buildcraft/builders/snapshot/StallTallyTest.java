/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * What a stalled build lists, and what it only counts: the cap on the list (the bound on what is synced every five
 * ticks), the order the reasons are kept in once there are more cells than the cap, and totals that never lose a cell.
 * A tally needs no world, so these stay plain unit tests; the game tests in {@link BuilderStallTester} cover what only
 * a world can show (which reason a real cell gets).
 */
public class StallTallyTest {

    private static final int CAP = SnapshotBuilder.STALL_CELL_CAP;

    private static BlockPos pos(int i) {
        return new BlockPos(i, 64, 0);
    }

    /**
     * Far more yellow cells than the cap, noted before the few red ones (the walk can meet the red cells last): the
     * list is exactly the cap, holds every red cell first in reason order, fills the rest with yellow in the order
     * noted, leaves the blue cells (after yellow in priority) out, and the totals count every cell noted.
     */
    @Test
    public void listIsCappedKeepsRedFirstAndTotalsCountEverything() {
        StallTally tally = new StallTally(CAP);
        int yellow = CAP * 4;
        for (int i = 0; i < yellow; i++) {
            tally.note(EnumStallReason.MISSING_ITEMS, pos(i));
        }
        tally.note(EnumStallReason.FLUID, pos(1000));
        tally.note(EnumStallReason.FLUID, pos(1001));
        tally.note(EnumStallReason.UNBREAKABLE, pos(2000));
        tally.note(EnumStallReason.CANT_STAND, pos(3000));
        tally.note(EnumStallReason.CANT_STAND, pos(3001));
        tally.note(EnumStallReason.REFUSED, pos(4000));

        List<StalledCell> cells = tally.cells();
        Assertions.assertEquals(CAP, cells.size(), "the list is cut to the cap");
        // Reason order (CANT_STAND, UNBREAKABLE, REFUSED), then the yellow in the order noted.
        Assertions.assertEquals(new StalledCell(pos(3000), EnumStallReason.CANT_STAND), cells.get(0));
        Assertions.assertEquals(new StalledCell(pos(3001), EnumStallReason.CANT_STAND), cells.get(1));
        Assertions.assertEquals(new StalledCell(pos(2000), EnumStallReason.UNBREAKABLE), cells.get(2));
        Assertions.assertEquals(new StalledCell(pos(4000), EnumStallReason.REFUSED), cells.get(3));
        for (int i = 4; i < CAP; i++) {
            Assertions.assertEquals(new StalledCell(pos(i - 4), EnumStallReason.MISSING_ITEMS), cells.get(i),
                    "the rest are the first yellow cells noted, in order");
        }
        Assertions.assertTrue(cells.stream().noneMatch(cell -> cell.reason() == EnumStallReason.FLUID),
                "blue comes after yellow in priority, so none fits under the cap");

        Assertions.assertEquals(2, tally.count(EnumStallReason.CANT_STAND));
        Assertions.assertEquals(1, tally.count(EnumStallReason.UNBREAKABLE));
        Assertions.assertEquals(1, tally.count(EnumStallReason.REFUSED));
        Assertions.assertEquals(yellow, tally.count(EnumStallReason.MISSING_ITEMS),
                "the total counts the cells past the cap too");
        Assertions.assertEquals(2, tally.count(EnumStallReason.FLUID),
                "a reason left out of the list is still counted");
    }

    /** Under the cap nothing is dropped, and the list is still in reason order, not in the order noted. */
    @Test
    public void underTheCapEveryCellIsListedInReasonOrder() {
        StallTally tally = new StallTally(CAP);
        tally.note(EnumStallReason.FLUID, pos(1));
        tally.note(EnumStallReason.MISSING_ITEMS, pos(2));
        tally.note(EnumStallReason.CANT_STAND, pos(3));

        Assertions.assertEquals(List.of(
                new StalledCell(pos(3), EnumStallReason.CANT_STAND),
                new StalledCell(pos(2), EnumStallReason.MISSING_ITEMS),
                new StalledCell(pos(1), EnumStallReason.FLUID)), tally.cells());
    }

    /** A cell with nothing but an ordinary wait in the way (reason {@code null}) is neither listed nor counted. */
    @Test
    public void aNullReasonNotesNothing() {
        StallTally tally = new StallTally(CAP);
        tally.note(null, pos(1));

        Assertions.assertTrue(tally.cells().isEmpty());
        for (EnumStallReason reason : EnumStallReason.values()) {
            Assertions.assertEquals(0, tally.count(reason), reason + " counts nothing");
        }
    }

    /** One reason alone is bounded by the cap too, however many cells it has: the list and the memory held, not the
     *  total. */
    @Test
    public void oneReasonAloneIsCappedButFullyCounted() {
        StallTally tally = new StallTally(3);
        for (int i = 0; i < 10; i++) {
            tally.note(EnumStallReason.CANT_STAND, pos(i));
        }

        Assertions.assertEquals(List.of(
                new StalledCell(pos(0), EnumStallReason.CANT_STAND),
                new StalledCell(pos(1), EnumStallReason.CANT_STAND),
                new StalledCell(pos(2), EnumStallReason.CANT_STAND)), tally.cells());
        Assertions.assertEquals(10, tally.count(EnumStallReason.CANT_STAND));
    }
}
