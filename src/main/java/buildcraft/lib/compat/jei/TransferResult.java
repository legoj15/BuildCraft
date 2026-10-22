/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.jei;

/**
 * The outcome of a JEI "+" transfer request, in the one vocabulary every BuildCraft machine's transfer core speaks
 * ({@code RoboticsTableTransfer}, {@code AssemblyTableTransfer}'s advisory, the bucket machines'). JEI-free, so the
 * server-side cores can return it and be tested headless; {@link AbstractTransferHandler} turns it into JEI's answer.
 */
public enum TransferResult {
    /** Done (or, for an advisory call, doable). The only result that moved — or could move — anything. */
    OK,
    /** The player does not hold what the request needs. */
    MISSING,
    /** The machine's slots hold something that would conflict; the player must empty them first. */
    BUSY,
    /** The request names a recipe, option or board the server does not know — stale or tampered. */
    INVALID
}
