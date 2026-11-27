/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

/**
 * A block entity that takes something from the world when it is placed (a working area) and would be left without it
 * when a blueprint builds it, because a blueprint build calls no placement hook ({@code onPlacedBy} and the like).
 *
 * <p>The blueprint build ({@code SchematicBlockDefault}) calls this on the server once the block is placed and its
 * saved data, if any, is loaded: also when the blueprint holds no saved data for the block, and when the load failed
 * and the block entity was replaced with a fresh one. The saved data has already had the original's working area
 * removed by the rules, so what the tile finds here to work on is its own, never the machine's it was copied from. An
 * exception thrown here is logged and dropped; it never reaches the server tick.
 *
 * <p>The tile has no owner yet: the saved data it loaded carries none ({@link BlueprintOwner#strip}), and whoever
 * built it stamps their own ({@link BlueprintOwner#stamp}) after the build returns, hook included. A hook must not act
 * as the owner (ask a claim mod, record an advancement) and must not rely on {@code getOwner()}.
 */
public interface BuiltFromBlueprint {
    /** Takes what a placement would have taken, for a tile that has not got it already. Server side only. */
    void onBuiltFromBlueprint();
}
