/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.platform.config.BCConfigType;
import buildcraft.platform.config.ConfigRegistrar;

/** {@link ConfigRegistrar} on NeoForge: registers the spec on BuildCraft's mod container. */
public final class NeoForgeConfigRegistrar implements ConfigRegistrar {

    @Override
    public void register(BCConfigType type, Object spec, String fileName) {
        var container = NeoForgeModBus.container();
        ModConfigSpec modSpec = (ModConfigSpec) spec;
        //? if >=26.3 {
        // FML 12 split COMMON into LOCAL (both sides, global file, never synced: the old COMMON behaviour) and
        // SYNCED, and names the file after the type ("-local"), so a caller keeping a legacy name passes it.
        ModConfig.Type fmlType = switch (type) {
            case CLIENT -> ModConfig.Type.CLIENT;
            case COMMON -> ModConfig.Type.SYNCED;
            case LOCAL -> ModConfig.Type.LOCAL;
        };
        if (fileName == null) {
            container.registerConfig(fmlType, modSpec);
        } else {
            container.registerConfig(fmlType, modSpec, fileName);
        }
        //?} else {
        /*// Before FML 12, COMMON is both sides and never synced, i.e. LOCAL's behaviour; the file name is fixed.
        ModConfig.Type fmlType = type == BCConfigType.CLIENT ? ModConfig.Type.CLIENT : ModConfig.Type.COMMON;
        container.registerConfig(fmlType, modSpec);
        *///?}
    }
}
//?}
