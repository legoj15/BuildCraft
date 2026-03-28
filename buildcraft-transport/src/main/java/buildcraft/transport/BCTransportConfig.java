/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeDefinition;

/** Transport module configuration. */
public class BCTransportConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue disableRfPipe;
    public static final ModConfigSpec.BooleanValue powerPipeUseOldMjTexture;

    /** MJ cost per item extracted by a wooden pipe. Default: 1 MJ (= 1_000_000 µMJ). */
    public static final ModConfigSpec.LongValue mjPerItem;

    /** MJ cost per millibucket extracted by a wooden fluid pipe. Default: 1000 µMJ per mB. */
    public static final ModConfigSpec.LongValue mjPerMillibucket;

    /** Base multiplier for kinesis pipe transfer rates (MJ). Default: 4. */
    public static final ModConfigSpec.IntValue basePowerRate;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        disableRfPipe = builder
                .comment("Set true to disable the RF pipe")
                .define("disableRfPipe", false);

        powerPipeUseOldMjTexture = builder
                .comment("Set true to use the old MJ texture for power pipes")
                .define("powerPipeUseOldMjTexture", false);

        builder.pop();
        builder.push("pipes");

        mjPerItem = builder
                .comment("MJ cost per item extracted by a wooden pipe. Default: 1 MJ (= 1,000,000 µMJ).")
                .defineInRange("mjPerItem", 1_000_000L, 0L, Long.MAX_VALUE);

        mjPerMillibucket = builder
                .comment("MJ cost per millibucket extracted by a wooden fluid pipe. Default: 1000 µMJ per mB.")
                .defineInRange("mjPerMillibucket", 1_000L, 0L, Long.MAX_VALUE);

        basePowerRate = builder
                .comment("Base multiplier for kinesis pipe transfer rates (MJ). Default: 4.")
                .defineInRange("basePowerRate", 4, 1, Integer.MAX_VALUE);

        builder.pop();
        SPEC = builder.build();
    }

    /** Register default power transfer info for all kinesis pipe definitions.
     *  Should be called after BCTransportPipes.preInit(). */
    public static void registerPowerTransferData() {
        int rate = basePowerRate.get();
        powerTransfer(BCTransportPipes.cobblePower, rate, 16, false);
        powerTransfer(BCTransportPipes.stonePower, rate * 2, 32, false);
        powerTransfer(BCTransportPipes.woodPower, rate * 4, 128, true);
        powerTransfer(BCTransportPipes.sandstonePower, rate * 4, 32, false);
        powerTransfer(BCTransportPipes.quartzPower, rate * 8, 32, false);
        powerTransfer(BCTransportPipes.ironPower, rate * 8, 32, false);
        powerTransfer(BCTransportPipes.goldPower, rate * 32, 32, false);
        powerTransfer(BCTransportPipes.diamondPower, rate * 64, 32, false);
        powerTransfer(BCTransportPipes.diaWoodPower, rate * 64, 32, true);
    }

    private static void powerTransfer(PipeDefinition def, int transferMultiplier, int resistanceDivisor, boolean recv) {
        long transfer = MjAPI.MJ * transferMultiplier;
        long resistance = MjAPI.MJ / resistanceDivisor;
        PipeApi.powerTransferData.put(def, PowerTransferInfo.createFromResistance(transfer, resistance, recv));
    }
}
