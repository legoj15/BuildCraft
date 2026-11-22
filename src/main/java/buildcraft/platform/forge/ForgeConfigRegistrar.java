/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import buildcraft.platform.config.BCConfigType;
import buildcraft.platform.config.ConfigRegistrar;

/^*
 * {@link ConfigRegistrar} on MinecraftForge: registers the spec through the loading context the mod's constructor
 * received (kept by {@link ForgeModBus}), where NeoForge registers on a container.
 *
 * <p><b>Types.</b> Forge's {@code ModConfig.Type} has {@code CLIENT}, {@code COMMON} and {@code SERVER} only (read from
 * FML 26.3's own jar); there is no {@code LOCAL} or {@code SYNCED}. {@code LOCAL} and {@code COMMON} both map to Forge's
 * {@code COMMON}: both sides, the global config directory, never synced, which is exactly what {@code LOCAL} means. The
 * one loss against NeoForge 26.3 is the one Fabric has, a {@code COMMON} spec that is not synced to clients. Forge's
 * {@code SERVER} is no closer: it is the only synced type, but it lives in each world's own config folder and is read
 * only when that world loads, so a spec read at mod load (BuildCraft reads its values from the first tick of loading)
 * would throw until then.
 *
 * <p><b>One per type.</b> A Forge mod container keeps its configs by type, so a second spec of a type would silently
 * replace the first in the container while still being tracked by FML's config tracker. {@code LOCAL} and {@code COMMON}
 * share Forge's {@code COMMON}, so registering both is refused here instead; nothing registers a {@code COMMON} spec yet
 * (see {@link BCConfigType#COMMON}).
 *
 * <p><b>File name.</b> Forge's context has the three-argument registration, so a caller's legacy name is honoured on
 * every line, with the {@code .toml} suffix included as given. Forge's default for {@code COMMON} is the same
 * {@code <modid>-common.toml} BuildCraft already uses, and for {@code CLIENT} {@code <modid>-client.toml}.
 *
 * <p>Forge reads the file in its own config-load phase, after every mod is constructed, so the loading event reaches a
 * listener subscribed while the constructor runs (see {@link ForgeConfigHooks}); nothing is loaded when this returns.
 *
 * <p>The specs are also kept in registration order for the hand-built config screen
 * ({@code platform/forge/client/ForgeConfigScreen}), as Forge offers no lookup of a mod's specs.
 *^/
public final class ForgeConfigRegistrar implements ConfigRegistrar {
    /^* The Forge types a spec is registered for already (a container keeps one config per type). ^/
    private final Set<ModConfig.Type> registered = EnumSet.noneOf(ModConfig.Type.class);
    /^* Every spec registered so far, in registration order. ^/
    private static final List<ForgeConfigSpec> SPECS = new CopyOnWriteArrayList<>();

    /^* The specs registered so far, in registration order (read by the config screen). ^/
    public static List<ForgeConfigSpec> registeredSpecs() {
        return List.copyOf(SPECS);
    }

    @Override
    public synchronized void register(BCConfigType type, Object spec, String fileName) {
        ModConfig.Type forgeType = switch (type) {
            case CLIENT -> ModConfig.Type.CLIENT;
            case COMMON, LOCAL -> ModConfig.Type.COMMON;
        };
        if (!registered.add(forgeType)) {
            throw new IllegalStateException("A " + forgeType + " config is already registered, and Forge keeps one per type "
                + "per mod: " + type + " maps to it too (see the class documentation)");
        }
        ForgeConfigSpec forgeSpec = (ForgeConfigSpec) spec;
        SPECS.add(forgeSpec);
        if (fileName == null) {
            ForgeModBus.context().registerConfig(forgeType, forgeSpec);
        } else {
            ForgeModBus.context().registerConfig(forgeType, forgeSpec, fileName);
        }
    }
}
*///?}
