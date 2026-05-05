package buildcraft.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Unified configuration for all BuildCraft modules.
 * Combines BCLibConfig, BCCoreConfig, BCTransportConfig, BCEnergyConfig, and BCBuildersConfig
 * into a single ModConfigSpec so there's one config file and one config button.
 *
 * Each module's ConfigValue fields remain in their original *Config class.
 * This class just orchestrates the builder so all sections are nested under
 * a single spec.
 */
public class BCUnifiedConfig {

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        buildcraft.lib.BCLibConfig.buildGeneral(builder);
        BCCoreConfig.buildGeneral(builder);
        buildcraft.transport.BCTransportConfig.buildGeneral(builder);
        buildcraft.energy.BCEnergyConfig.buildGeneral(builder);
        buildcraft.builders.BCBuildersConfig.buildGeneral(builder);
        builder.pop();

        // display.* moved to BCUnifiedClientConfig (CLIENT scope) — per-player UI prefs
        // should not be server-authoritative.

        builder.push("worldgen");
        BCCoreConfig.buildWorldgen(builder);
        buildcraft.energy.BCEnergyConfig.buildWorldgen(builder);
        builder.pop();

        SPEC = builder.build();
    }
}
