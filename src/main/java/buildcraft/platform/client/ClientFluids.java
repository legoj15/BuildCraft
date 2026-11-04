/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import net.minecraft.resources.Identifier;

import buildcraft.api.fluids.BCFluidStack;

/** Client-only: how the loader draws a fluid stack (its still texture and its tint). */
public interface ClientFluids {
    /** The name of the still sprite the loader declares for the stack's fluid, or null if it declares none. */
    Identifier stillTexture(BCFluidStack stack);

    /** The stack's ARGB tint as the loader reports it (it may carry zero alpha); opaque white for no tint. */
    int tint(BCFluidStack stack);
}
