/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import buildcraft.platform.config.BCConfigType;
import buildcraft.platform.config.ConfigRegistrar;

/^*
 * {@link ConfigRegistrar} on Fabric, through Forge Config API Port, which ships NeoForge's {@code ModConfigSpec} and
 * {@code ModConfig} on Fabric (so BC's config classes stay as they are) and reads and writes the same toml files.
 *
 * <p><b>Gap:</b> FCAP has no {@code LOCAL} type, only NeoForge's pre-FML-12 set. {@code LOCAL} and {@code COMMON} both
 * map to its {@code COMMON}, which is both sides, one global file and never synced: exactly {@code LOCAL}'s behaviour.
 * The one loss is that a NeoForge 26.3 {@code COMMON} (FML 12 {@code SYNCED}) spec is not synced to clients on Fabric.
 *^/
public final class FabricConfigRegistrar implements ConfigRegistrar {
    static final String MOD_ID = "buildcraftunofficial";

    @Override
    public void register(BCConfigType type, Object spec, String fileName) {
        ModConfig.Type fcapType = type == BCConfigType.CLIENT ? ModConfig.Type.CLIENT : ModConfig.Type.COMMON;
        IConfigSpec configSpec = (IConfigSpec) spec;
        if (fileName == null) {
            ConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec);
        } else {
            ConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec, fileName);
        }
    }
}
*///?}
