/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Cross-version accessor for a {@link ResourceKey}'s id.
 * <p>
 * MC 1.21.11 renamed {@code ResourceKey.location()} to {@code identifier()} as part of the
 * {@code ResourceLocation -> Identifier} sweep. Centralising the accessor here keeps that single
 * divergence in one place instead of at every call site (the helper pattern used by
 * {@link PositionUtil} / {@link MessageUtil} for other cross-line API renames).
 */
public final class RegistryKeyUtil {
    /** The id of {@code key} — {@code key.identifier()} on 1.21.11+, {@code key.location()} on 1.21.10. */
    public static Identifier id(ResourceKey<?> key) {
        //? if >=1.21.11 {
        return key.identifier();
        //?} else {
        /*return key.location();*/
        //?}
    }

    /**
     * Pass-through overload for nodes where {@code RecipeHolder.id()} already yields an
     * {@link Identifier} (1.21.1, whose RecipeManager is keyed by {@code ResourceLocation}) rather
     * than a {@link ResourceKey}. Lets {@code RegistryKeyUtil.id(holder.id())} stay version-neutral
     * at the call site — Java overload resolution picks this by the argument's actual type. Unused
     * (and unreachable) on nodes whose {@code id()} returns a {@code ResourceKey}.
     */
    public static Identifier id(Identifier id) {
        return id;
    }

    private RegistryKeyUtil() {}
}
