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
        buildcraft.transport.BCTransportConfig.buildDisplay(builder);
        buildcraft.energy.BCEnergyConfig.buildDisplay(builder);
        builder.pop();

        SPEC = builder.build();
    }
}
