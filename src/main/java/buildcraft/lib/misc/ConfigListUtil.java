/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The config option shapes whose builder call is not the same on every loader. One call site per option keeps the
 * loader split in this file (#209) instead of one directive block at each of them.
 */
public final class ConfigListUtil {
    private ConfigListUtil() {}

    /**
     * Defines a list of strings that may be left empty. The builder's element supplier (what NeoForge's config screen
     * adds for a new row) is an empty string where the builder takes one, and the Forge builder has no such argument.
     *
     * @param builder the builder, already carrying the option's comment
     * @param path the option's key
     * @param defaults the value of an unedited option
     */
    public static ModConfigSpec.ConfigValue<List<? extends String>> stringList(
            ModConfigSpec.Builder builder, String path, List<? extends String> defaults) {
        //? if neoforge || fabric {
        return builder.defineListAllowEmpty(path, defaults, () -> "", s -> s instanceof String);
        //?} elif forge {
        /*return builder.defineListAllowEmpty(path, defaults, s -> s instanceof String);
        *///?}
    }
}
