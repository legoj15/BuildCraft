/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Unified CLIENT-scope configuration for BuildCraft.
 * Holds per-player display preferences that should not be server-authoritative —
 * each client keeps its own copy in {@code config/buildcraftunofficial-client.toml}.
 *
 * Gameplay/balance options live in {@link BCUnifiedConfig} (COMMON scope).
 */
public class BCUnifiedClientConfig {

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("display");
        buildcraft.energy.BCEnergyConfig.buildDisplay(builder);
        buildcraft.lib.BCLibConfig.buildDisplay(builder);
        builder.pop();

        SPEC = builder.build();
    }
}
