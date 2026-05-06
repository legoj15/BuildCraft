/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import mezz.jei.api.recipe.types.IRecipeType;

/**
 * Holds the JEI {@link IRecipeType} for the Assembly Table. Every entry in the
 * category — chipsets, gates, lenses, plugs, gate copier, and the unified
 * cycling facade entry — is an {@link AssemblyRecipeJei}.
 */
public final class AssemblyRecipeJeiTypes {
    public static final IRecipeType<AssemblyRecipeJei> ASSEMBLY = IRecipeType.create(
            "buildcraftunofficial", "assembly_table", AssemblyRecipeJei.class);

    private AssemblyRecipeJeiTypes() {}
}
