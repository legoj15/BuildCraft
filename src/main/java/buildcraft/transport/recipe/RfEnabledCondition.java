/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.recipe;

import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

import buildcraft.core.BCUnifiedConfig;
import buildcraft.transport.BCTransportConfig;

/**
 * Recipe condition that drops BuildCraft's RF (Forge Energy) <em>pipe</em> recipes at datapack load
 * when the {@code disableRfPipe} config toggle is set — both the forward kinesis&rarr;RF crafts and the
 * reverse RF&rarr;kinesis downgrades — so that turning the feature off also makes the pipes uncraftable
 * (absent from the recipe book and JEI) rather than only making placed pipes inert.
 *
 * <p>The MJ Dynamo (MJ&rarr;FE) and RF Engine (FE&rarr;MJ) are deliberately <strong>not</strong> gated: they
 * are the only MJ&harr;FE conversion bridges, so they must stay craftable for BuildCraft to interoperate
 * with another mod's energy system (which is the whole point of disabling BC's own RF pipes).
 *
 * <p>Conditions are evaluated server-side during the datapack reload ({@code RecipeManager.prepare}),
 * after configs are loaded, and the filtered recipe set is what syncs to clients — so this gate is
 * server-authoritative with no client/server divergence. It is a load-time snapshot: toggling the
 * config mid-session only takes effect on the next world load or {@code /reload}.
 *
 * <p>The condition carries no data, so it serialises as the empty object
 * {@code { "type": "buildcraftunofficial:rf_enabled" }}. It never touches {@link IContext} (whose
 * shape diverges across MC lines), so the class needs no Stonecutter directives and compiles
 * identically on every node.
 */
public final class RfEnabledCondition implements ICondition {
    public static final RfEnabledCondition INSTANCE = new RfEnabledCondition();

    /** No-data condition: always decodes to the singleton and encodes nothing. */
    public static final MapCodec<RfEnabledCondition> CODEC = MapCodec.unit(INSTANCE);

    private RfEnabledCondition() {}

    @Override
    public boolean test(IContext context) {
        // Guard against the spec not being loaded yet (e.g. if a datagen path is ever added):
        // ModConfigSpec.BooleanValue#get() throws IllegalStateException when the config is unloaded,
        // which would crash recipe loading. Default to "RF enabled" (recipes present) in that case,
        // matching the disableRfPipe=false default.
        if (!BCUnifiedConfig.SPEC.isLoaded()) {
            return true;
        }
        return !BCTransportConfig.disableRfPipe.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "rf_enabled";
    }
}
