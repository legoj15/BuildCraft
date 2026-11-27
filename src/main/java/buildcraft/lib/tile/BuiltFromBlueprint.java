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
 * <p>Whoever built the block calls this on the server once the schematic's build is over
 * ({@link BlueprintOwner#finishBuild}: the Builder, a player placing a single schematic): the block is placed and its
 * saved data, if any, loaded, also when the blueprint holds no saved data for the block and when the load failed and
 * the block entity was replaced with a fresh one. The saved data has already had the original's working area removed
 * by the rules, so what the tile finds here to work on is its own, never the machine's it was copied from. An
 * exception thrown here is logged and dropped; it never reaches the server tick. A caller of a schematic's
 * {@code build} that does not finish the build through {@link BlueprintOwner} gives the tile neither its owner nor
 * this call.
 *
 * <p>The owner is set when this runs: the saved data carried none ({@link BlueprintOwner#strip}), and
 * {@link BlueprintOwner#finishBuild} gives the tile its builder's (null for a builder nobody owns, which acts as the
 * mod's own profile) just before calling this. A hook acts as that owner: what it breaks or takes, it asks claim mods
 * about as {@code getOwner()}, as the same tile would after a placement. It must not remove or replace its own block:
 * the owner was set on this block entity, and a new one would have none.
 */
public interface BuiltFromBlueprint {
    /** Takes what a placement would have taken, for a tile that has not got it already. Server side only. */
    void onBuiltFromBlueprint();
}
