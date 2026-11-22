/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that overrides (or implements) a hook a loader patches into a vanilla type, such as NeoForge's
 * {@code IBlockExtension#getFlammability}. Such an override imports nothing from the loader, so only this tag (and
 * the platform census, {@code scripts/platform-census.py}) records that another loader needs its own route to the
 * same behaviour: a mixin, a registry, or a callback. Keep {@code @Override} beside it, so a loader without the hook
 * fails the build loudly instead of silently never calling the method. Keep the tagged body a one-line delegate to a
 * common method, so that other route can call the same logic.
 *
 * <p>An override that two loaders both call under one name (its arm opens {@code //? if neoforge || forge}) lists a
 * hook per loader, so a grep for {@code forge:} finds every Forge hook BuildCraft answers; an arm keyed to one loader
 * (the Forge form of a hook whose signature differs, written beside the NeoForge one) carries that loader's tag alone.
 *
 * <p>Source retention: this is a bookkeeping tag, it never reaches the class files.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface LoaderHook {
    /**
     * One {@code "<loader>:<patched interface>#<method>"} per loader that calls the method, e.g.
     * {@code "neoforge:IBlockExtension#getFlammability"} and {@code "forge:IForgeBlock#getFlammability"}.
     */
    String[] value();
}
