/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

/**
 * Why a stalled Builder is not getting on with one cell of its build; see {@link SnapshotBuilder#getStalledCells()}.
 * The order is the order the highlights are kept in when there are more than the cap: what the player has to fix
 * themselves comes before what only waits on supplies or on fluid.
 */
public enum EnumStallReason {
    /** The block cannot stand where the blueprint has it (a torch over air the blueprint also leaves empty, a bed or
     *  door whose other half has no room): placing it would only be undone, so the Builder never tries. */
    CANT_STAND(Hue.RED, "cant_stand"),
    /** A block that is in the way is bedrock or the like, or a protection mod forbids breaking it. */
    UNBREAKABLE(Hue.RED, "unbreakable"),
    /** A protection mod refused the placement, and the cell is parked until its window ends. */
    REFUSED(Hue.RED, "refused"),
    /** The Builder's resources lack the items (or fluid) the block needs. */
    MISSING_ITEMS(Hue.YELLOW, "missing_items"),
    /** Fluid in the build area holds the cell back (see the place-task fluid gate), or sits in the cell where the
     *  fluid mode never removes it. */
    FLUID(Hue.BLUE, "fluid");

    /** The colour a highlight is drawn in: red for what the player has to change, yellow for supplies, blue for
     *  fluid. Kept here, not in the renderer, so the server-side tests can say which reasons count as red. */
    public enum Hue {
        RED, YELLOW, BLUE
    }

    private static final EnumStallReason[] VALUES = values();

    private final Hue hue;
    private final String langKey;

    EnumStallReason(Hue hue, String name) {
        this.hue = hue;
        this.langKey = "gui.buildcraftunofficial.builder.stalled." + name;
    }

    public Hue hue() {
        return hue;
    }

    /** The key of this reason's phrase in a stalled summary, taking the count as its one {@code %s}. */
    public String langKey() {
        return langKey;
    }

    /** The reason with this ordinal, or {@code null} for a value no reason has (a newer or damaged sync). */
    public static EnumStallReason fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
