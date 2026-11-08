/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.neoforged.fml.config.IConfigSpec;
//? if >=1.21.10 {
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
//?} else {
/^import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
^///?}
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
 *
 * <p>FCAP reads such a spec's file inside the register call, not in a later loading phase as FML does, so the spec is
 * loaded (and its loading event fired) when {@link #register} returns; {@link FabricConfigHooks#onLoad} accounts for it.
 *^/
public final class FabricConfigRegistrar implements ConfigRegistrar {
    static final String MOD_ID = "buildcraftunofficial";

    @Override
    public void register(BCConfigType type, Object spec, String fileName) {
        ModConfig.Type fcapType = type == BCConfigType.CLIENT ? ModConfig.Type.CLIENT : ModConfig.Type.COMMON;
        IConfigSpec configSpec = (IConfigSpec) spec;
        //? if >=1.21.10 {
        if (fileName == null) {
            ConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec);
        } else {
            ConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec, fileName);
        }
        //?} else {
        /^// Forge Config API Port 21.1 keeps the NeoForge registry under its own versioned package.
        if (fileName == null) {
            NeoForgeConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec);
        } else {
            NeoForgeConfigRegistry.INSTANCE.register(MOD_ID, fcapType, configSpec, fileName);
        }
        ^///?}
    }
}
*///?}
