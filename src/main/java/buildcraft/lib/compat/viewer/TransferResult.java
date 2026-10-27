/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.viewer;

/**
 * The outcome of a recipe viewer's "+" transfer request, in the one vocabulary every BuildCraft machine's transfer core
 * speaks ({@code RoboticsTableTransfer}, {@code AssemblyTableTransfer}'s advisory, the bucket machines'). Viewer-free, so
 * the server-side cores can return it and be tested headless; {@link RecipeTransfer} runs the check, and each viewer's
 * handler (JEI's {@code AbstractTransferHandler}, REI's {@code ReiRecipeTransferHandler}) turns it into its answer.
 */
public enum TransferResult {
    /** Done (or, for an advisory call, doable). The only result that moved — or could move — anything. */
    OK,
    /** The player does not hold what the request needs. */
    MISSING,
    /** The player does not hold what the request needs, but does hold a programmed board or robot that would have
     *  served had it been blank. Programmed ones are never taken (the player may value one), so this says why
     *  instead of the generic {@link #MISSING}. */
    ONLY_PROGRAMMED,
    /** The machine's slots hold something that would conflict and the transfer would clear it back into the
     *  player's inventory, but the inventory has no room for it all. Nothing was moved. */
    BUSY,
    /** The request names a recipe, option or board the server does not know — stale or tampered. */
    INVALID
}
