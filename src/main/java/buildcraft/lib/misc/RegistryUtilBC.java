/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/** Version-neutral registry-value lookup by id.
 *
 * <p>On 1.21.10+ {@code Registry.getValue(ResourceLocation)} returns the value directly (and
 * {@code Registry.get(ResourceLocation)} returns an {@code Optional<Holder.Reference<T>>}). On 1.21.1
 * the {@code getValue} method does not exist and {@code Registry.get(ResourceLocation)} returns the value
 * directly. This helper hides that split so call sites stay version-neutral. */
public class RegistryUtilBC {
    /** @return the registered value for {@code id}, or {@code null} if nothing is registered under it. */
    public static <T> T getValue(Registry<T> registry, Identifier id) {
        //? if >=1.21.10 {
        return registry.getValue(id);
        //?} else {
        /*return registry.get(id);*/
        //?}
    }

    /** A {@link net.minecraft.core.HolderGetter} over the block registry, e.g. for
     *  {@code NbtUtils.readBlockState}. On 1.21.10+ the registry directly IS-A {@code HolderGetter}; on
     *  1.21.1 it must be adapted via {@code asLookup()}. */
    public static net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup() {
        //? if >=1.21.10 {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();*/
        //?}
    }
}
